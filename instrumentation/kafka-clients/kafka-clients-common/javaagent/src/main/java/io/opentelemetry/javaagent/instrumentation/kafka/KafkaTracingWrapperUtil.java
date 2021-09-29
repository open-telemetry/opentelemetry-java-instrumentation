/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafka;

public final class KafkaTracingWrapperUtil {
  private static final ThreadLocal<Boolean> wrappingEnabled = ThreadLocal.withInitial(() -> true);

  private KafkaTracingWrapperUtil() {}

  public static void enableWrapping() {
    wrappingEnabled.set(true);
  }

  public static void disableWrapping() {
    wrappingEnabled.set(false);
  }

  public static boolean wrappingEnabled() {
    return wrappingEnabled.get() == true;
  }
}
