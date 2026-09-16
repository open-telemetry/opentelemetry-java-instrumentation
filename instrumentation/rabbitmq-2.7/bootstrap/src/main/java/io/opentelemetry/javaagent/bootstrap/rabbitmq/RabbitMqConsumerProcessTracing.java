/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;

/** Coordinates process telemetry between Spring Rabbit and RabbitMQ instrumentations. */
public final class RabbitMqConsumerProcessTracing {

  private static final ScopedThreadValue<Boolean> currentProcessSpanSuppression =
      new ScopedThreadValue<>();

  public static ScopedThreadValue<Boolean> currentProcessSpanSuppression() {
    return currentProcessSpanSuppression;
  }

  public static boolean shouldTraceProcess() {
    return currentProcessSpanSuppression.get() == null;
  }

  private RabbitMqConsumerProcessTracing() {}
}
