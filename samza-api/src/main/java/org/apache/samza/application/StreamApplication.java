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
package org.apache.samza.application;

import java.io.IOException;
import org.apache.samza.annotation.InterfaceStability;
import org.apache.samza.config.Config;
import org.apache.samza.config.MapConfig;
import org.apache.samza.job.ApplicationStatus;
import org.apache.samza.metrics.MetricsReporter;
import org.apache.samza.operators.MessageStream;
import org.apache.samza.operators.StreamDescriptor;
import org.apache.samza.operators.StreamGraph;
import org.apache.samza.operators.OutputStream;
import org.apache.samza.runtime.ApplicationRunner;
import org.apache.samza.runtime.ApplicationRuntimeResult;
import org.apache.samza.serializers.Serde;
import org.apache.samza.task.StreamTask;

import java.util.Map;


/**
 * Describes and initializes the transforms for processing message streams and generating results.
 * <p>
 * The following example removes page views older than 1 hour from the input stream:
 * <pre>{@code
 * public class PageViewCounter {
 *   public static void main(String[] args) {
 *     CommandLine cmdLine = new CommandLine();
 *     Config config = cmdLine.loadConfig(cmdLine.parser().parse(args));
 *     StreamApplication app = StreamApplications.createStreamApp(config);
 *     MessageStream<PageViewEvent> pageViewEvents =
 *       app.openInput("pageViewEvents", (k, m) -> (PageViewEvent) m);
 *     OutputStream<String, PageViewEvent, PageViewEvent> recentPageViewEvents =
 *       app.openOutput("recentPageViewEvents", m -> m.memberId, m -> m);
 *
 *     pageViewEvents
 *       .filter(m -> m.getCreationTime() > System.currentTimeMillis() - Duration.ofHours(1).toMillis())
 *       .sendTo(filteredPageViewEvents);
 *     app.run();
 *     app.waitForFinish();
 *   }
 * }
 * }</pre>
 *
 * <p>
 * Implementation Notes: Currently StreamApplications are wrapped in a {@link StreamTask} during execution.
 * A new instance of the user operator DAG will be created and initialized when creating each
 * {@link StreamTask} instance used for processing incoming messages. Execution is synchronous and thread-safe within
 * each {@link StreamTask}.
 *
 * <p>
 * Functions implemented for transforms in StreamApplications ({@link org.apache.samza.operators.functions.MapFunction},
 * {@link org.apache.samza.operators.functions.FilterFunction} for e.g.) are initable and closable. They are initialized
 * before messages are delivered to them and closed after their execution when the {@link StreamTask} instance is closed.
 * See {@link org.apache.samza.operators.functions.InitableFunction} and {@link org.apache.samza.operators.functions.ClosableFunction}.
 */
@InterfaceStability.Unstable
public class StreamApplication implements ApplicationRunnable {

  /*package private*/
  final ApplicationRunner runner;
  final Config config;
  final StreamGraph graph;

  StreamApplication(ApplicationRunner runner, Config config) {
    this.runner = runner;
    this.config = config;
    this.graph = runner.createGraph();
  }

  @Override
  public ApplicationRuntimeResult run() {
    return this.runner.run(this);
  }

  @Override
  public ApplicationRuntimeResult kill() {
    return this.runner.kill(this);
  }

  @Override
  public ApplicationStatus status() {
    return this.runner.status(this);
  }

  public static class AppConfig extends MapConfig {

    public static final String APP_NAME = "app.name";
    public static final String APP_ID = "app.id";
    public static final String APP_CLASS = "app.class";
    public static final String RUNNER_CONFIG = "app.runner.class";
    private static final String DEFAULT_RUNNER_CLASS = "org.apache.samza.runtime.RemoteApplicationRunner";

    public static final String JOB_NAME = "job.name";
    public static final String JOB_ID = "job.id";

    public AppConfig(Config config) {
      super(config);
    }

    public String getAppName() {
      return get(APP_NAME, get(JOB_NAME));
    }

    public String getAppId() {
      return get(APP_ID, get(JOB_ID, "1"));
    }

    public String getAppClass() {
      return get(APP_CLASS, null);
    }

    public String getApplicationRunnerClass() {
      return get(RUNNER_CONFIG, DEFAULT_RUNNER_CLASS);
    }

    /**
     * returns full application id
     * @return full app id
     */
    public String getGlobalAppId() {
      return String.format("app-%s-%s", getAppName(), getAppId());
    }

  }

  /**
   * Set {@link MetricsReporter}s for this {@link StreamApplication}
   *
   * @param metricsReporters the map of {@link MetricsReporter}s to be added
   * @return this {@link StreamApplication} instance
   */
  public StreamApplication withMetricsReporters(Map<String, MetricsReporter> metricsReporters) {
    this.runner.addMetricsReporters(metricsReporters);
    return this;
  }

  /**
   * Return the globally unique application ID for this {@link StreamApplication}
   *
   * @return the globally unique appplication ID
   */
  public String getGlobalAppId() {
    return new AppConfig(config).getGlobalAppId();
  }

  /**
   * Gets the input {@link MessageStream} corresponding to the {@code streamId}.
   * <p>
   * Multiple invocations of this method with the same {@code streamId} will throw an {@link IllegalStateException}.
   *
   * @param <M> the type of input messages
   * @param streamId the input {@link StreamDescriptor.Input}
   * @param serde the {@link Serde} object used to deserialize input messages
   * @return the input {@link MessageStream}
   * @throws IllegalStateException when invoked multiple times with the same {@code streamId}
   * @throws IOException when fail to create a serializable input operator to read the input messages
   */
  public final <M> MessageStream<M> openInput(String streamId, Serde<M> serde) throws IOException {
    return this.graph.getInputStream(streamId, serde);
  }

  /**
   * Gets the input {@link MessageStream} corresponding to the {@code streamId}.
   * <p>
   * Multiple invocations of this method with the same {@code streamId} will throw an {@link IllegalStateException}.
   *
   * @param <M> the type of message in the input {@link MessageStream}
   * @param streamId the input {@link StreamDescriptor.Input}
   * @return the input {@link MessageStream}
   * @throws IllegalStateException when invoked multiple times with the same {@code streamId}
   * @throws IOException when fail to create a serializable input operator to read the input messages
   */
  public final <M> MessageStream<M> openInput(String streamId) throws IOException {
    return this.graph.getInputStream(streamId);
  }

  /**
   * Gets the {@link OutputStream} corresponding to the {@code streamId}.
   * <p>
   * Multiple invocations of this method with the same {@code streamId} will throw an {@link IllegalStateException}.
   *
   * @param <M> the type of message in the {@link OutputStream}
   * @param output the {@link StreamDescriptor.Output} to describe the {@code output} object
   * @param serde the {@link Serde} object used to serialize output messages
   * @return the output {@link OutputStream}
   * @throws IllegalStateException when invoked multiple times with the same {@code streamId}
   */
  public final <M> OutputStream<M> openOutput(String output, Serde<M> serde) {
    return this.graph.getOutputStream(output, serde);
  }

  /**
   * Gets the {@link OutputStream} corresponding to the {@code streamId}.
   * <p>
   * Multiple invocations of this method with the same {@code streamId} will throw an {@link IllegalStateException}.
   *
   * @param <M> the type of message in the {@link OutputStream}
   * @param output the {@link StreamDescriptor.Output} to describe the {@code output} object
   * @return the output {@link OutputStream}
   * @throws IllegalStateException when invoked multiple times with the same {@code streamId}
   */
  public final <M> OutputStream<M> openOutput(String output) {
    return this.graph.getOutputStream(output);
  }

}
