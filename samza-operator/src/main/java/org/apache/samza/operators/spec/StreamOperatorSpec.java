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
import org.apache.samza.operators.data.MessageEnvelope;
import org.apache.samza.operators.functions.FlatMapFunction;
import org.apache.samza.task.TaskContext;


/**
 * The spec for a linear stream operator that outputs 0 or more {@link MessageEnvelope}s for each input {@link MessageEnvelope}.
 *
 * @param <M>  the type of input {@link MessageEnvelope}
 * @param <OM>  the type of output {@link MessageEnvelope}
 */
public class StreamOperatorSpec<M extends MessageEnvelope, OM extends MessageEnvelope> implements OperatorSpec<OM> {

  private final OperatorSpec.OpCode opCode;

  private final int opId;

  private final MessageStreamImpl outputStream;

  private final FlatMapFunction<M, OM> transformFn;

  /**
   * Constructor for a {@link StreamOperatorSpec} that accepts an output {@link MessageStreamImpl}.
   *
   * @param transformFn  the transformation function
   * @param outputStream  the output {@link MessageStreamImpl}
   */
  StreamOperatorSpec(FlatMapFunction<M, OM> transformFn, MessageStreamImpl outputStream, OperatorSpec.OpCode opCode, int opId) {
    this.outputStream = outputStream;
    this.transformFn = transformFn;
    this.opCode = opCode;
    this.opId = opId;
  }

  @Override
  public MessageStreamImpl getOutputStream() {
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
