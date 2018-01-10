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
package org.apache.samza.runtime;

import org.apache.samza.annotation.InterfaceStability;
import org.apache.samza.application.StreamApplication;
import org.apache.samza.application.StreamApplication.AppConfig;
import org.apache.samza.config.Config;
import org.apache.samza.config.ConfigException;
import org.apache.samza.job.ApplicationStatus;
import org.apache.samza.metrics.MetricsReporter;
import org.apache.samza.operators.StreamGraph;

import java.lang.reflect.Constructor;
import java.util.Map;
import org.apache.samza.system.StreamSpec;


/**
 * The primary means of managing execution of the {@link StreamApplication} at runtime.
 */
@InterfaceStability.Unstable
public interface ApplicationRunner {

  /**
   * Static method to load the {@link ApplicationRunner}
   *
   * @param config  configuration passed in to initialize the Samza processes
   * @return  the configure-driven {@link ApplicationRunner} to run the user-defined stream applications
   */
  static ApplicationRunner fromConfig(Config config) {
    AppConfig appCfg = new AppConfig(config);
    try {
      Class<?> runnerClass = Class.forName(appCfg.getApplicationRunnerClass());
      if (ApplicationRunner.class.isAssignableFrom(runnerClass)) {
        Constructor<?> constructor = runnerClass.getConstructor(Config.class); // *sigh*
        return (ApplicationRunner) constructor.newInstance(config);
      }
    } catch (Exception e) {
      throw new ConfigException(String.format("Problem in loading ApplicationRunner class %s",
          appCfg.getApplicationRunnerClass()), e);
    }
    throw new ConfigException(String.format(
        "Class %s does not extend ApplicationRunner properly",
        appCfg.getApplicationRunnerClass()));
  }

  /**
   * Deploy and run the Samza jobs to execute {@link org.apache.samza.task.StreamTask}.
   * It is non-blocking so it doesn't wait for the application running.
   * This method assumes you task.class is specified in the configs.
   *
   * NOTE. this interface will most likely change in the future.
   */
  @Deprecated
  void runTask();

  /**
   * The method to run the application. The implementation of this method has to be a non-blocking function.
   *
   * @param app the user application to run
   * @return {@link ApplicationRuntimeResult} object that user can choose to wait for finish
   */
  ApplicationRuntimeResult run(StreamApplication app);

  /**
   * The method to kill the application. The implementation of this method has to be a non-blocking function.
   *
   * @param app the user application to kill
   * @return {@link ApplicationRuntimeResult} object that user can choose to wait for finish
   */
  ApplicationRuntimeResult kill(StreamApplication app);

  /**
   * The method to query the status of the application. The implementation of this method has to be non-blocking function.
   *
   * @param app the user application to query for status
   * @return the {@link ApplicationStatus} of the user application
   */
  ApplicationStatus status(StreamApplication app);

  /**
   * Create an empty {@link StreamGraph} object to instantiate the user defined operator DAG.
   *
   * @return the empty {@link StreamGraph} object to be instantiated
   */
  StreamGraph createGraph();

  /**
   * Method to add a set of customized {@link MetricsReporter}s in the application
   *
   * @param metricsReporters the map of customized {@link MetricsReporter}s objects to be used
   */
  void addMetricsReporters(Map<String, MetricsReporter> metricsReporters);

  /**
   * Method to get {@link StreamSpec} from the system configuration
   *
   * @param streamId unique ID to identify a stream
   * @return the {@link StreamSpec} object that describes the physical details of the stream
   */
  StreamSpec getStreamSpec(String streamId);

}
