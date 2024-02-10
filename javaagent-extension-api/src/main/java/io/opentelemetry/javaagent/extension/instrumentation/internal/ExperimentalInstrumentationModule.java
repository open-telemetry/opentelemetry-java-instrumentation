/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import static java.util.Collections.emptyList;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public interface ExperimentalInstrumentationModule {

  /**
   * Only functional for Modules where {@link InstrumentationModule#isIndyModule()} returns {@code
   * true}.
   *
   * <p>Normally, helper and advice classes are loaded in a child classloader of the instrumented
   * classloader. This method allows to inject classes directly into the instrumented classloader
   * instead.
   *
   * @param injector the builder for injecting classes
   */
  default void injectClasses(ClassInjector injector) {}

  /**
   * Returns a list of helper classes that will be defined in the class loader of the instrumented
   * library.
   */
  default List<String> injectedClassNames() {
    return emptyList();
  }

  /**
   * By default every InstrumentationModule is loaded by an isolated classloader, even if multiple
   * modules instrument the same application classloader.
   *
   * <p>Sometimes this is not desired, e.g. when instrumenting modular libraries such as the AWS
   * SDK. In such cases the {@link InstrumentationModule}s which want to share a classloader can
   * return the same group name from this method.
   */
  default String getModuleGroup() {
    return getClass().getName();
  }
}
