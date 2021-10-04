/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

public final class KafkaClientsConsumerProcessTracing {
  private static final ThreadLocal<Boolean> wrappingEnabled = ThreadLocal.withInitial(() -> true);

  private KafkaClientsConsumerProcessTracing() {}

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
