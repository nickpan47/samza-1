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

import org.apache.samza.config.Config;
import org.apache.samza.operators.MessageStreamImpl;
import org.apache.samza.operators.functions.FlatMapFunction;
import org.apache.samza.task.TaskContext;


/**
 * The spec for a linear stream operator that outputs 0 or more messages for each input message.
 *
 * @param <M>  the type of input message
 * @param <OM>  the type of output message
 */
public class StreamOperatorSpec<M, OM> implements OperatorSpec<OM> {

  /**
   * {@link OpCode} for this {@link StreamOperatorSpec}
   */
  private final OperatorSpec.OpCode opCode;

  /**
   * The unique ID for this operator.
   */
  private final int opId;

  /**
   * The output {@link MessageStreamImpl} from this {@link StreamOperatorSpec}
   */
  private final MessageStreamImpl<OM> outputStream;

  /**
   * Transformation function applied in this {@link StreamOperatorSpec}
   */
  private final FlatMapFunction<M, OM> transformFn;

  /**
   * Constructor for a {@link StreamOperatorSpec} that accepts an output {@link MessageStreamImpl}.
   *
   * @param transformFn  the transformation function
   * @param outputStream  the output {@link MessageStreamImpl}
   * @param opCode  the {@link OpCode} for this {@link StreamOperatorSpec}
   * @param opId  the unique id for this {@link StreamOperatorSpec} in a {@link org.apache.samza.operators.StreamGraph}
   */
  StreamOperatorSpec(FlatMapFunction<M, OM> transformFn, MessageStreamImpl outputStream, OperatorSpec.OpCode opCode, int opId) {
    this.outputStream = outputStream;
    this.transformFn = transformFn;
    this.opCode = opCode;
    this.opId = opId;
  }

  @Override
  public MessageStreamImpl<OM> getNextStream() {
    return this.outputStream;
  }

  public FlatMapFunction<M, OM> getTransformFn() {
    return this.transformFn;
  }

  public OperatorSpec.OpCode getOpCode() {
    return this.opCode;
  }

  public int getOpId() {
    return this.opId;
  }

  @Override
  public void init(Config config, TaskContext context) {
    this.transformFn.init(config, context);
  }
}
