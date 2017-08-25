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
package org.apache.samza.task;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.samza.application.StreamApplicationInternal;
import org.apache.samza.config.Config;
import org.apache.samza.control.ControlMessageListenerTask;
import org.apache.samza.control.Watermark;
import org.apache.samza.operators.ContextManager;
import org.apache.samza.operators.StreamGraphImpl;
import org.apache.samza.operators.impl.InputOperatorImpl;
import org.apache.samza.operators.impl.OperatorImplGraph;
import org.apache.samza.control.IOGraph;
import org.apache.samza.system.IncomingMessageEnvelope;
import org.apache.samza.system.SystemStream;
import org.apache.samza.util.Clock;
import org.apache.samza.util.SystemClock;


/**
 * A {@link StreamTask} implementation that brings all the operator API implementation components together and
 * feeds the input messages into the user-defined transformation chains in {@link StreamApplicationInternal}.
 */
public final class StreamOperatorTask implements StreamTask, InitableTask, WindowableTask, ClosableTask, ControlMessageListenerTask {

  private final StreamApplicationInternal streamApplication;
  private final Clock clock;

  private OperatorImplGraph operatorImplGraph;
  private ContextManager contextManager;
  private IOGraph ioGraph;

  /**
   * Constructs an adaptor task to run the user-implemented {@link StreamApplicationInternal}.
   * @param streamApplication the user-implemented {@link StreamApplicationInternal} that creates the logical DAG
   * @param clock the {@link Clock} to use for time-keeping
   */
  public StreamOperatorTask(StreamApplicationInternal streamApplication, Clock clock) {
    this.streamApplication = streamApplication;
    this.clock = clock;
  }

  public StreamOperatorTask(StreamApplicationInternal application) {
    this(application, SystemClock.instance());
  }

  /**
   * Initializes this task during startup.
   * <p>
   * Implementation: Initializes the user-implemented {@link StreamApplicationInternal}. The {@link StreamApplicationInternal} sets
   * the input and output streams and the task-wide context manager using the {@link StreamGraphImpl} APIs,
   * and the logical transforms using the {@link org.apache.samza.operators.MessageStream} APIs. It then uses
   * the {@link StreamGraphImpl} to create the {@link OperatorImplGraph} corresponding to the logical DAG.
   *
   * @param config allows accessing of fields in the configuration files that this StreamTask is specified in
   * @param context allows initializing and accessing contextual data of this StreamTask
   * @throws Exception in case of initialization errors
   */
  @Override
  public final void init(Config config, TaskContext context) throws Exception {
    // TODO: getStreamGraphImpl() need to return a new instance of StreamGraphImpl per task, not a shared instance
    StreamGraphImpl streamGraph = this.streamApplication.getStreamGraphImpl();
    // initialize the user-implemented stream application.
    // this.streamApplication.init(streamGraph, config);

    // get the user-implemented context manager and initialize it
    // NOTE: if we don't clone for each task, global variables used across different tasks are possible. If we clone
    // the context manager for each task, the shared context is only across operators in the same task instance. I am ignoring
    // the global shared variable in this case and only focus on shared context within a single task instance for now.
    this.contextManager = streamGraph.getContextManager().getContextManagerPerTask();
    if (this.contextManager != null) {
      this.contextManager.init(config, context);
    }

    // create the operator impl DAG corresponding to the logical operator spec DAG
    this.operatorImplGraph = new OperatorImplGraph(streamGraph, config, context, clock);
    this.ioGraph = streamGraph.toIOGraph();
  }

  /**
   * Passes the incoming message envelopes along to the {@link InputOperatorImpl} node
   * for the input {@link SystemStream}.
   * <p>
   * From then on, each {@link org.apache.samza.operators.impl.OperatorImpl} propagates its transformed output to
   * its chained {@link org.apache.samza.operators.impl.OperatorImpl}s itself.
   *
   * @param ime incoming message envelope to process
   * @param collector the collector to send messages with
   * @param coordinator the coordinator to request commits or shutdown
   */
  @Override
  public final void process(IncomingMessageEnvelope ime, MessageCollector collector, TaskCoordinator coordinator) {
    SystemStream systemStream = ime.getSystemStreamPartition().getSystemStream();
    InputOperatorImpl inputOpImpl = operatorImplGraph.getInputOperator(systemStream);
    if (inputOpImpl != null) {
      inputOpImpl.onMessage(Pair.of(ime.getKey(), ime.getMessage()), collector, coordinator);
    }
  }

  @Override
  public final void window(MessageCollector collector, TaskCoordinator coordinator)  {
    operatorImplGraph.getAllInputOperators()
        .forEach(inputOperator -> inputOperator.onTimer(collector, coordinator));
  }

  @Override
  public IOGraph getIOGraph() {
    return ioGraph;
  }

  @Override
  public final void onWatermark(Watermark watermark,
      SystemStream systemStream,
      MessageCollector collector,
      TaskCoordinator coordinator) {
    InputOperatorImpl inputOpImpl = operatorImplGraph.getInputOperator(systemStream);
    if (inputOpImpl != null) {
      inputOpImpl.onWatermark(watermark, collector, coordinator);
    }
  }

  @Override
  public void close() throws Exception {
    if (this.contextManager != null) {
      this.contextManager.close();
    }
    operatorImplGraph.close();
  }

  /* package private for testing */
  OperatorImplGraph getOperatorImplGraph() {
    return this.operatorImplGraph;
  }
}
