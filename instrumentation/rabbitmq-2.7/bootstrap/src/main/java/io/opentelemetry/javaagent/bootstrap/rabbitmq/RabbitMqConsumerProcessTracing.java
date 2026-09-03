/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

/** Coordinates process telemetry ownership between Spring Rabbit and RabbitMQ instrumentations. */
public final class RabbitMqConsumerProcessTracing {

  private static final ThreadLocal<Boolean> wrappingEnabled = ThreadLocal.withInitial(() -> true);

  public static boolean setWrappingEnabled(boolean enabled) {
    boolean previous = wrappingEnabled.get();
    wrappingEnabled.set(enabled);
    return previous;
  }

  public static boolean isWrappingEnabled() {
    return wrappingEnabled.get();
  }

  private RabbitMqConsumerProcessTracing() {}
}
