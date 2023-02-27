/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.spring;

public final class SpringSchedulingTaskTracing {

  private static final ThreadLocal<Boolean> wrappingEnabled = ThreadLocal.withInitial(() -> true);

  private SpringSchedulingTaskTracing() {}

  public static boolean setEnabled(boolean enabled) {
    boolean previous = wrappingEnabled.get();
    wrappingEnabled.set(enabled);
    return previous;
  }

  public static boolean wrappingEnabled() {
    return wrappingEnabled.get();
  }
}
