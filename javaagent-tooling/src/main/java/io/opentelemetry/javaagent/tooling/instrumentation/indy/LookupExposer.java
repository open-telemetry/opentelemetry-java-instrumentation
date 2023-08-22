/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.instrumentation.indy;

import java.lang.invoke.MethodHandles;

/**
 * This class is injected into every {@link InstrumentationModuleClassLoader} so that the bootstrap
 * can use a {@link MethodHandles.Lookup} with a lookup class from within the {@link
 * InstrumentationModuleClassLoader}, instead of calling {@link MethodHandles#lookup()} which uses
 * the caller class as the lookup class.
 *
 * <p>This circumvents a nasty JVM bug that's described <a
 * href="https://github.com/elastic/apm-agent-java/issues/1450">here</a>. The error is reproduced in
 * {@code InstrumentationModuleClassLoaderTest}
 */
public class LookupExposer {

  private LookupExposer() {}

  public static MethodHandles.Lookup getLookup() {
    return MethodHandles.lookup();
  }
}
