/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import javax.annotation.Nullable;

/** Coordinates process telemetry between Spring Rabbit and RabbitMQ instrumentations. */
public final class RabbitMqConsumerProcessTracing {

  private static final ScopedThreadValue<Boolean> springProcessTelemetry =
      new ScopedThreadValue<>();

  @Nullable
  public static Boolean setSpringProcessTelemetry(boolean enabled) {
    Boolean previous = springProcessTelemetry.get();
    return springProcessTelemetry.set(enabled ? Boolean.TRUE : previous);
  }

  public static void restoreSpringProcessTelemetry(@Nullable Boolean previous) {
    springProcessTelemetry.restore(previous);
  }

  public static boolean shouldTraceProcess() {
    return springProcessTelemetry.get() == null;
  }

  private RabbitMqConsumerProcessTracing() {}
}
