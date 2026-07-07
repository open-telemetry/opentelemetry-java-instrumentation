/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.bytebuddy.utility.JavaModule;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface ExperimentalInstrumentationModule {

  /**
   * Register virtual field. First argument for the consumer is dot class name of the type where the
   * field is added and the second argument is the dot class name of the field type.
   */
  default void registerVirtualFields(BiConsumer<String, String> virtualFieldRegistrar) {}

  /**
   * Some instrumentations need to invoke classes which are present both in the agent classloader
   * and the instrumented application classloader. By default, the classloader of the
   * instrumentation would link those against the class provided by the agent. This setting allows
   * to hide packages, so that matching classes are instead used from the application classloader.
   *
   * @return the list of packages (without trailing dots)
   */
  default List<String> agentPackagesToHide() {
    return emptyList();
  }

  /**
   * Some instrumentation need to access JPMS modules that are not accessible by default, this
   * method provides a way to access those classes like the "--add-opens" JVM command.
   *
   * @return map of module to open as key, list of packages as value.
   */
  // TODO: when moving this method outside of experimental API, we need to decide using JavaModule
  // instance or a class FQN in the map entry, as it could lead to some limitations
  default Map<JavaModule, List<String>> jpmsModulesToOpen() {
    return emptyMap();
  }
}
