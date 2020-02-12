/*
 * Copyright 2019 Datadog
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datadog.profiling.controller;

import datadog.trace.api.Config;
import java.lang.reflect.InvocationTargetException;
import lombok.extern.slf4j.Slf4j;

/** Factory used to get a {@link Controller}. */
@Slf4j
public final class ControllerFactory {

  /**
   * Returns the created controller.
   *
   * @return the created controller.
   * @throws UnsupportedEnvironmentException if there is controller available for the platform we're
   *     running in. See the exception message for specifics.
   */
  public static Controller createController(final Config config)
      throws UnsupportedEnvironmentException {
    try {
      Class.forName("com.oracle.jrockit.jfr.Producer");
      throw new UnsupportedEnvironmentException(
          "The JFR controller is currently not supported on the Oracle JDK <= JDK 11!");
    } catch (final ClassNotFoundException e) {
      // Fall through - until we support Oracle JDK 7 & 8, this is a good thing. ;)
    }
    try {
      final Class<? extends Controller> clazz =
          Class.forName("com.datadog.profiling.controller.openjdk.OpenJdkController")
              .asSubclass(Controller.class);
      return clazz.getDeclaredConstructor(Config.class).newInstance(config);
    } catch (final ClassNotFoundException
        | NoSuchMethodException
        | InstantiationException
        | IllegalAccessException
        | InvocationTargetException e) {
      throw new UnsupportedEnvironmentException(
          "The JFR controller could not find a supported JFR API", e);
    }
  }
}
