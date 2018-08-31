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

import org.apache.samza.SamzaException;
import org.apache.samza.application.ApplicationDescriptor;
import org.apache.samza.application.ApplicationDescriptorImpl;
import org.apache.samza.application.ApplicationDescriptors;
import org.apache.samza.application.TaskApplicationDescriptorImpl;
import org.apache.samza.config.Config;
import org.apache.samza.config.ConfigException;
import org.apache.samza.config.TaskConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ExecutorService;

import static org.apache.samza.util.ScalaJavaUtil.toScalaFunction;

/**
 * This class provides utility functions to load task factory classes based on config, and to wrap {@link StreamTaskFactory}
 * in {@link AsyncStreamTaskFactory} when running {@link StreamTask}s in multi-thread mode
 */
public class TaskFactoryUtil {
  private static final Logger log = LoggerFactory.getLogger(TaskFactoryUtil.class);

  /**
   * Creates a {@link TaskFactory} based on {@link ApplicationDescriptorImpl}
   *
   * @param appDesc {@link ApplicationDescriptorImpl} for this application
   * @return {@link TaskFactory} object defined by {@code appDesc}
   */
  public static TaskFactory getTaskFactory(ApplicationDescriptorImpl<? extends ApplicationDescriptor> appDesc) {
    return ApplicationDescriptors.<TaskFactory>forType(TaskApplicationDescriptorImpl::getTaskFactory,
        streamAppDesc -> (StreamTaskFactory) () ->
            new StreamOperatorTask(streamAppDesc.getOperatorSpecGraph(), streamAppDesc.getContextManager()),
        appDesc);
  }

  /**
   * Creates a {@link TaskFactory} based on the configuration.
   * <p>
   * This should only be used to create {@link TaskFactory} defined in task.class
   *
   * @param config  the {@link Config} for this job
   * @return  a {@link TaskFactory} object, either a instance of {@link StreamTaskFactory} or {@link AsyncStreamTaskFactory}
   */
  public static TaskFactory getTaskFactoryFromConfig(Config config) {
    // if there is configuration to set the job w/ a specific type of task, instantiate the corresponding task factory
    String taskClassName = new TaskConfig(config).getTaskClass().getOrElse(toScalaFunction(
      () -> {
        throw new ConfigException("No task class defined in the configuration.");
      }));

    log.info("Got task class name: {}", taskClassName);

    boolean isAsyncTaskClass;
    try {
      isAsyncTaskClass = AsyncStreamTask.class.isAssignableFrom(Class.forName(taskClassName));
    } catch (Throwable t) {
      throw new ConfigException(String.format("Invalid configuration for AsyncStreamTask class: %s", taskClassName), t);
    }

    if (isAsyncTaskClass) {
      return (AsyncStreamTaskFactory) () -> {
        try {
          return (AsyncStreamTask) Class.forName(taskClassName).newInstance();
        } catch (Throwable t) {
          log.error("Error loading AsyncStreamTask class: {}. error: {}", taskClassName, t);
          throw new SamzaException(String.format("Error loading AsyncStreamTask class: %s", taskClassName), t);
        }
      };
    }

    return (StreamTaskFactory) () -> {
      try {
        return (StreamTask) Class.forName(taskClassName).newInstance();
      } catch (Throwable t) {
        log.error("Error loading StreamTask class: {}. error: {}", taskClassName, t);
        throw new SamzaException(String.format("Error loading StreamTask class: %s", taskClassName), t);
      }
    };
  }

  /**
   * Optionally wrap the {@link StreamTaskFactory} in a {@link AsyncStreamTaskFactory}, when running {@link StreamTask}
   * in multi-thread mode.
   *
   * @param factory  the task factory instance loaded according to the task class
   * @param singleThreadMode  the flag indicating whether the job is running in single thread mode or not
   * @param taskThreadPool  the thread pool to run the {@link AsyncStreamTaskAdapter} tasks
   * @return  the finalized task factory object
   */
  public static TaskFactory finalizeTaskFactory(TaskFactory factory, boolean singleThreadMode, ExecutorService taskThreadPool) {

    validateFactory(factory);

    boolean isAsyncTaskClass = factory instanceof AsyncStreamTaskFactory;
    if (isAsyncTaskClass) {
      log.info("Got an AsyncStreamTask implementation.");
    }

    if (singleThreadMode && isAsyncTaskClass) {
      throw new SamzaException("AsyncStreamTask cannot run on single thread mode.");
    }

    if (!singleThreadMode && !isAsyncTaskClass) {
      log.info("Converting StreamTask to AsyncStreamTaskAdapter when running StreamTask with multiple threads");
      return (AsyncStreamTaskFactory) () -> new AsyncStreamTaskAdapter(((StreamTaskFactory) factory).createInstance(), taskThreadPool);
    }

    return factory;
  }

  private static void validateFactory(TaskFactory factory) {
    if (factory == null) {
      throw new SamzaException("Either the task class name or the task factory instance is required.");
    }

    if (!(factory instanceof StreamTaskFactory) && !(factory instanceof AsyncStreamTaskFactory)) {
      throw new SamzaException(String.format("TaskFactory must be either StreamTaskFactory or AsyncStreamTaskFactory. %s is not supported",
          factory.getClass()));
    }
  }

}
