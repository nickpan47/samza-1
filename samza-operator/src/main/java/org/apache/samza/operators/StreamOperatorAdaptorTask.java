/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.samza.operators;

import org.apache.samza.Partition;
import org.apache.samza.config.Config;
import org.apache.samza.operators.data.IncomingSystemMessageEnvelope;
import org.apache.samza.operators.data.MessageEnvelope;
import org.apache.samza.operators.impl.OperatorImpl;
import org.apache.samza.operators.impl.OperatorImpls;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.system.SystemStreamPartition;
import org.apache.samza.task.InitableTask;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.StreamTask;
import org.apache.samza.task.TaskContext;
import org.apache.samza.task.TaskCoordinator;
import org.apache.samza.task.WindowableTask;

import java.util.*;


/**
 * An {@link StreamTask} implementation that receives {@link IncomingSystemMessageEnvelope}s and propagates them
 * through the user's stream transformations defined in {@link StreamOperatorTask#transform(Map)} using the
 * {@link MessageStream} APIs.
 * <p>
 * This class brings all the operator API implementation components together and feeds the
 * {@link IncomingSystemMessageEnvelope}s into the transformation chains.
 * <p>
 * It accepts an instance of the user implemented {@link StreamOperatorTask}. When its own {@link #init(Config, TaskContext)}
 * method is called during startup, it creates a {@link MessageStreamImpl} corresponding to each of its input
 * {@link SystemStreamPartition}s and then calls the user's {@link StreamOperatorTask#transform(Map)} method.
 * <p>
 * When users invoke the methods on the {@link MessageStream} API to describe their stream transformations in the
 * {@link StreamOperatorTask#transform(Map)} method, the underlying {@link MessageStreamImpl} creates the
 * corresponding {@link org.apache.samza.operators.spec.OperatorSpec} to record information about the desired
 * transformation, and returns the output {@link MessageStream} to allow further transform chaining.
 * <p>
 * Once the user's transformation DAGs have been described for all {@link MessageStream}s (i.e., when the
 * {@link StreamOperatorTask#transform(Map)} call returns), it calls
 * {@link OperatorImpls#createOperatorImpls(MessageStreamImpl, TaskContext)} for each of the input
 * {@link MessageStreamImpl}. This instantiates the {@link org.apache.samza.operators.impl.OperatorImpl} DAG
 * corresponding to the aforementioned {@link org.apache.samza.operators.spec.OperatorSpec} DAG and returns the
 * root node of the DAG, which this class saves.
 * <p>
 * Now that it has the root for the DAG corresponding to each {@link SystemStreamPartition}, it can pass the message
 * envelopes received in {@link StreamTask#process(IncomingMessageEnvelope, MessageCollector, TaskCoordinator)} along
 * to the appropriate root nodes. From then on, each {@link org.apache.samza.operators.impl.OperatorImpl} propagates
 * its transformed output to the next set of {@link org.apache.samza.operators.impl.OperatorImpl}s.
 */
public final class StreamOperatorAdaptorTask implements StreamTask, InitableTask, WindowableTask {

  /**
   * A mapping from each {@link SystemStreamPartition} to the root node of its operator chain DAG.
   */
  private final Map<SystemStreamPartition, OperatorImpl<IncomingSystemMessageEnvelope, ? extends MessageEnvelope>> operatorChains = new HashMap<>();

  private final StreamOperatorTask userTask;

  public StreamOperatorAdaptorTask(StreamOperatorTask userTask) {
    this.userTask = userTask;
  }

  @Override
  public final void init(Config config, TaskContext context) throws Exception {
    if (this.userTask instanceof InitableTask) {
      ((InitableTask) this.userTask).init(config, context);
    }
    MessageStreamsBuilderImpl mstreamsBuilder = new MessageStreamsBuilderImpl();
    Map<SystemStream, Map<Partition, MessageStreamImpl<IncomingSystemMessageEnvelope>>> inputBySystemStream = new HashMap<>();
    context.getSystemStreamPartitions().forEach(ssp -> {
      if (!inputBySystemStream.containsKey(ssp.getSystemStream())) {
        inputBySystemStream.putIfAbsent(ssp.getSystemStream(), new HashMap<>());
      }
      inputBySystemStream.get(ssp.getSystemStream()).put(ssp.getPartition(), new MessageStreamImpl<>(mstreamsBuilder));
    });
    // invoke to wire-up user-defined stream processing DAG
    this.userTask.transform(mstreamsBuilder);
    // replace the input stream of DAG w/ merged system stream partitions in a single task
    inputBySystemStream.forEach((ss, parMap) -> mstreamsBuilder.swapInputStream(ss, merge(ss, parMap)));
    context.getSystemStreamPartitions().forEach(ssp -> operatorChains.put(ssp,
        OperatorImpls.createOperatorImpls(inputBySystemStream.get(ssp.getSystemStream()).get(ssp.getPartition()), context)));
  }

  private MessageStream<IncomingSystemMessageEnvelope> merge(SystemStream ss, Map<Partition, MessageStreamImpl<IncomingSystemMessageEnvelope>> parMap) {
    // Here we will assume that the program is at {@link SystemStream} level. Hence, any two partitions from the same {@link SystemStream}
    // that are assigned (grouped) in the same task will be "merged" to the same operator instances that consume the {@link SystemStream}

    List<MessageStream<IncomingSystemMessageEnvelope>> moreInputs = new ArrayList<>();
    Iterator<MessageStreamImpl<IncomingSystemMessageEnvelope>> streamIterator = parMap.values().iterator();
    MessageStreamImpl<IncomingSystemMessageEnvelope> mergedStream = streamIterator.next();
    streamIterator.forEachRemaining(m -> moreInputs.add((MessageStreamImpl<IncomingSystemMessageEnvelope>) m));
    if (moreInputs.size() > 0) {
      mergedStream = (MessageStreamImpl<IncomingSystemMessageEnvelope>) mergedStream.merge(moreInputs);
    }
    // Now swap the input stream in taskStreamBuilder w/ the mergedStream
    return mergedStream;
  }

  @Override
  public final void process(IncomingMessageEnvelope ime, MessageCollector collector, TaskCoordinator coordinator) {
    this.operatorChains.get(ime.getSystemStreamPartition())
        .onNext(new IncomingSystemMessageEnvelope(ime), collector, coordinator);
  }

  @Override
  public final void window(MessageCollector collector, TaskCoordinator coordinator) throws Exception {
    if (this.userTask instanceof WindowableTask) {
      ((WindowableTask) this.userTask).window(collector, coordinator);
    }
  }
}
