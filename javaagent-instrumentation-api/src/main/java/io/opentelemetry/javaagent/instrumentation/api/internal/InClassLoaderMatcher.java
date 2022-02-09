/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InClassLoaderMatcher {

  private static final ThreadLocal<MutableBoolean> inClassLoaderMatcher =
      ThreadLocal.withInitial(MutableBoolean::new);

  private InClassLoaderMatcher() {}

  /**
   * Returns whether the ClassLoaderMatcher is currently executing.
   *
   * <p>This is used (at least) by the {@code internal-eclipse-osgi} instrumentation in order to
   * suppress a side effect in the Eclipse OSGi class loader that occurs when ClassLoaderMatcher
   * calls ClassLoader.getResource(). See {@code EclipseOsgiInstrumentationModule} for more details.
   */
  public static boolean get() {
    return inClassLoaderMatcher.get().value;
  }

  /**
   * WARNING This should not be used by instrumentation. It should only be used by
   * io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.
   *
   * <p>The reason it can't be (easily) hidden is that this class needs to live in the bootstrap
   * class loader to be accessible to instrumentation, while the ClassLoaderMatcher lives in the
   * agent class loader.
   */
  public static boolean getAndSet(boolean value) {
    return inClassLoaderMatcher.get().getAndSet(value);
  }

  /**
   * WARNING This should not be used by instrumentation. It should only be used by
   * io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.
   *
   * <p>The reason it can't be (easily) hidden is that this class needs to live in the bootstrap
   * class loader to be accessible to instrumentation, while the ClassLoaderMatcher lives in the
   * agent class loader.
   */
  public static void set(boolean value) {
    inClassLoaderMatcher.get().value = value;
  }

  // using MutableBoolean to avoid an extra thread local lookup for getAndSet()
  private static class MutableBoolean {

    private boolean value;

    private boolean getAndSet(boolean value) {
      boolean oldValue = this.value;
      this.value = value;
      return oldValue;
    }
  }
}
