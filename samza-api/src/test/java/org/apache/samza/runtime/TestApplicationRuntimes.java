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

import java.util.HashMap;
import java.util.Map;
import org.apache.samza.application.StreamApplication;
import org.apache.samza.application.StreamApplicationSpec;
import org.apache.samza.config.Config;
import org.apache.samza.config.MapConfig;
import org.apache.samza.runtime.internal.ApplicationRunner;
import org.apache.samza.runtime.internal.TestApplicationRunner;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;


/**
 * Unit test for {@link ApplicationRuntimes}
 */
public class TestApplicationRuntimes {
  @Test
  public void testGetApplicationRuntime() {
    StreamApplication mockApp = mock(StreamApplication.class);
    Map<String, String> configMap = new HashMap<>();
    configMap.put("app.runner.class", TestApplicationRunner.class.getName());
    Config config = new MapConfig(configMap);
    ApplicationRuntime appRuntime = ApplicationRuntimes.getApplicationRuntime(mockApp, config);
    StreamApplicationSpec appSpec = (StreamApplicationSpec) Whitebox.getInternalState(appRuntime, "appSpec");
    ApplicationRunner appRunner = (ApplicationRunner) Whitebox.getInternalState(appRuntime, "runner");
    assertEquals(appSpec.getConfig(), config);
    assertTrue(appRunner instanceof TestApplicationRunner);
  }
}
