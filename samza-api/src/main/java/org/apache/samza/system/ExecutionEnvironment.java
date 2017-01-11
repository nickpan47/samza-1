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
package org.apache.samza.system;

import org.apache.samza.annotation.InterfaceStability;
import org.apache.samza.config.Config;
import org.apache.samza.operators.MessageStreamGraph;


/**
 * Interface to be implemented by physical execution engine to deploy the config and jobs to run the {@link MessageStreamGraph}
 */
@InterfaceStability.Unstable
public interface ExecutionEnvironment {

  static ExecutionEnvironment getLocalEnvironment(Config config) {
    return null;
  }

  static ExecutionEnvironment getRemoteEnvironment(Config config) { return null; }

  MessageStreamGraph createGraph(Config config);

  /**
   * Method to be invoked to deploy and run the actual Samza jobs to execute {@link MessageStreamGraph}
   *
   * @param graph  the user-defined operator DAG
   */
  void run(MessageStreamGraph graph);
}
