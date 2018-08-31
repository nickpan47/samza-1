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

import java.util.function.Function;
import org.apache.samza.config.Config;


/**
 * Util class to help creating {@link ApplicationDescriptorImpl} instance from {@link SamzaApplication} and {@link Config}
 */
public class ApplicationDescriptors {
  private ApplicationDescriptors() {

  }

  /**
   * Create a new instance of {@link ApplicationDescriptorImpl} based on {@link SamzaApplication} and {@link Config}
   *
   * @param userApp the user-implemented {@link SamzaApplication}. The {@code userApp} has to have a proper fully-qualified class name.
   * @param config the user-supplied {@link Config} for the the application
   * @return the {@link ApplicationDescriptorImpl} instance containing the user processing logic and the config
   */
  public static ApplicationDescriptorImpl<? extends ApplicationDescriptor> getAppDescriptor(SamzaApplication userApp, Config config) {
    if (userApp instanceof StreamApplication) {
      return new StreamApplicationDescriptorImpl((StreamApplication) userApp, config);
    }
    if (userApp instanceof TaskApplication) {
      return new TaskApplicationDescriptorImpl((TaskApplication) userApp, config);
    }
    throw new IllegalArgumentException(String.format("User application class %s is not supported. Only StreamApplication "
        + "and TaskApplication are supported.", userApp.getClass().getName()));
  }

  public static <T> T forType(Function<TaskApplicationDescriptorImpl, T> forTaskApp, Function<StreamApplicationDescriptorImpl, T> forStreamApp,
      ApplicationDescriptorImpl<? extends ApplicationDescriptor> desc) {
    if (desc instanceof TaskApplicationDescriptorImpl) {
      return forTaskApp.apply((TaskApplicationDescriptorImpl) desc);
    } else if (desc instanceof StreamApplicationDescriptorImpl) {
      return forStreamApp.apply((StreamApplicationDescriptorImpl) desc);
    }

    throw new IllegalArgumentException(String.format("AppDescriptorImpl has to be either TaskAppDescriptorImpl or StreamAppDescriptorImpl."
        + " class %s is not supported", desc.getClass().getName()));
  }
}