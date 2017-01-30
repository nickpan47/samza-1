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
package org.apache.samza.operators.spec;

import org.apache.samza.operators.MessageStreamImpl;
import org.apache.samza.operators.StreamGraphImpl;
import org.apache.samza.operators.TestMessageEnvelope;
import org.apache.samza.operators.TestMessageStreamImplUtil;
import org.apache.samza.operators.data.MessageEnvelope;
import org.apache.samza.operators.functions.FlatMapFunction;
import org.apache.samza.operators.functions.PartialJoinFunction;
import org.apache.samza.operators.functions.SinkFunction;
import org.apache.samza.operators.MessageStreamImpl;
import org.apache.samza.operators.windows.internal.WindowInternal;
import org.apache.samza.operators.windows.WindowKey;
import org.apache.samza.operators.windows.WindowPane;
import org.apache.samza.storage.kv.Entry;
import org.apache.samza.task.MessageCollector;
import org.apache.samza.task.TaskCoordinator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;


public class TestOperatorSpecs {
  @Test
  public void testGetStreamOperator() {
    FlatMapFunction<MessageEnvelope, TestMessageEnvelope> transformFn = m -> new ArrayList<TestMessageEnvelope>() { {
          this.add(new TestMessageEnvelope(m.getKey().toString(), m.getMessage().toString(), 12345L));
        } };
    MessageStreamImpl<TestMessageEnvelope> mockOutput = mock(MessageStreamImpl.class);
    StreamOperatorSpec<MessageEnvelope, TestMessageEnvelope> strmOp = OperatorSpecs.createStreamOperator(transformFn, mockOutput,
        OperatorSpec.OpCode.FLAT_MAP, 0);
    assertEquals(strmOp.getTransformFn(), transformFn);
    assertEquals(strmOp.getOutputStream(), mockOutput);
  }

  @Test
  public void testGetSinkOperator() {
    SinkFunction<TestMessageEnvelope> sinkFn = (TestMessageEnvelope message, MessageCollector messageCollector,
          TaskCoordinator taskCoordinator) -> { };
    SinkOperatorSpec<TestMessageEnvelope> sinkOp = OperatorSpecs.createSinkOperator(sinkFn, OperatorSpec.OpCode.SINK, 0);
    assertEquals(sinkOp.getSinkFn(), sinkFn);
    assertTrue(sinkOp.getOutputStream() == null);
  }

  @Test
  public void testGetWindowOperator() throws Exception {
    Function<TestMessageEnvelope, String> keyExtractor = m -> "globalkey";
    BiFunction<TestMessageEnvelope, Integer, Integer> aggregator = (m, c) -> c + 1;

    //instantiate a window using reflection
    WindowInternal window = new WindowInternal(null, aggregator, keyExtractor, null);

    MessageStreamImpl<WindowPane<WindowKey<String>, Integer>> mockWndOut = mock(MessageStreamImpl.class);
    WindowOperatorSpec spec = OperatorSpecs.<TestMessageEnvelope, String, WindowKey<String>, Integer,
        WindowPane<WindowKey<String>, Integer>>createWindowOperatorSpec(window, mockWndOut, 0);
    assertEquals(spec.getWindow(), window);
    assertEquals(spec.getWindow().getKeyExtractor(), keyExtractor);
    assertEquals(spec.getWindow().getFoldFunction(), aggregator);
  }

  @Test
  public void testGetPartialJoinOperator() {
    PartialJoinFunction<MessageEnvelope<Object, ?>, MessageEnvelope<Object, ?>, TestMessageEnvelope> merger =
        (MessageEnvelope<Object, ?> m1, MessageEnvelope<Object, ?> m2) ->
            new TestMessageEnvelope(m1.getKey().toString(), m2.getMessage().toString(), System.nanoTime());

    StreamGraphImpl mockGraph = mock(StreamGraphImpl.class);
    MessageStreamImpl<TestMessageEnvelope> joinOutput = TestMessageStreamImplUtil
        .<TestMessageEnvelope>getMessageStreamImpl(mockGraph);
    PartialJoinOperatorSpec<MessageEnvelope<Object, ?>, Object, MessageEnvelope<Object, ?>, TestMessageEnvelope> partialJoin =
        OperatorSpecs.createPartialJoinOperatorSpec(merger, joinOutput, 0);

    assertEquals(partialJoin.getOutputStream(), joinOutput);
    MessageEnvelope<Object, Object> m = mock(MessageEnvelope.class);
    MessageEnvelope<Object, Object> s = mock(MessageEnvelope.class);
    assertEquals(partialJoin.getTransformFn(), merger);
  }

  @Test
  public void testGetMergeOperator() {
    StreamGraphImpl mockGraph = mock(StreamGraphImpl.class);
    MessageStreamImpl<TestMessageEnvelope> output = TestMessageStreamImplUtil.<TestMessageEnvelope>getMessageStreamImpl(mockGraph);
    StreamOperatorSpec<TestMessageEnvelope, TestMessageEnvelope> mergeOp = OperatorSpecs.createMergeOperator(output, 0);
    Function<TestMessageEnvelope, Collection<TestMessageEnvelope>> mergeFn = t -> new ArrayList<TestMessageEnvelope>() { {
        this.add(t);
      } };
    TestMessageEnvelope t = mock(TestMessageEnvelope.class);
    assertEquals(mergeOp.getTransformFn().apply(t), mergeFn.apply(t));
    assertEquals(mergeOp.getOutputStream(), output);
  }
}
