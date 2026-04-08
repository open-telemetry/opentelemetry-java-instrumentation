/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.spring;

public final class SpringSchedulingTaskTracing {

  private static final ThreadLocal<Boolean> wrappingEnabled = ThreadLocal.withInitial(() -> true);

  private SpringSchedulingTaskTracing() {}

  /**
   * @deprecated use {@link #setWrappingEnabled(boolean)} instead.
   */
  @Deprecated
  public static boolean setEnabled(boolean enabled) {
    return setWrappingEnabled(enabled);
  }

  public static boolean setWrappingEnabled(boolean enabled) {
    boolean previous = wrappingEnabled.get();
    wrappingEnabled.set(enabled);
    return previous;
  }

  public static boolean isWrappingEnabled() {
    return wrappingEnabled.get();
  }
}
