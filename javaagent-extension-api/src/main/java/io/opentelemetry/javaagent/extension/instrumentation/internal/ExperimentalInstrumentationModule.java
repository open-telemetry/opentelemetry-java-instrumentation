/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.internal.injection.ClassInjector;

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
}
