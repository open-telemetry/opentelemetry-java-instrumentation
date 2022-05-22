/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.internal;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class InClassLoaderMatcher {

  // using boolean[] to avoid an extra thread local lookup for getAndSet() below
  private static final ThreadLocal<boolean[]> inClassLoaderMatcher =
      ThreadLocal.withInitial(() -> new boolean[1]);

  private InClassLoaderMatcher() {}

  /**
   * Returns whether the ClassLoaderMatcher is currently executing.
   *
   * <p>This is used (at least) by the {@code internal-eclipse-osgi} instrumentation in order to
   * suppress a side effect in the Eclipse OSGi class loader that occurs when ClassLoaderMatcher
   * calls ClassLoader.getResource(). See {@code EclipseOsgiInstrumentationModule} for more details.
   */
  public static boolean get() {
    return inClassLoaderMatcher.get()[0];
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
    boolean[] arr = inClassLoaderMatcher.get();
    boolean curr = arr[0];
    arr[0] = value;
    return curr;
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
    inClassLoaderMatcher.get()[0] = value;
  }
}
