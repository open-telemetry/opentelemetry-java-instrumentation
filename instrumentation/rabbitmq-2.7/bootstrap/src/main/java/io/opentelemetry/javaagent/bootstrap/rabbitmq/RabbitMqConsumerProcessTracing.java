/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;

/** Coordinates process telemetry between Spring Rabbit and RabbitMQ instrumentations. */
public final class RabbitMqConsumerProcessTracing {

  private static final ScopedThreadValue<Boolean> processSpanSuppression =
      new ScopedThreadValue<>();

  public static ScopedThreadValue<Boolean> processSpanSuppression() {
    return processSpanSuppression;
  }

  public static boolean isProcessSpanSuppressed() {
    return processSpanSuppression.get() != null;
  }

  private RabbitMqConsumerProcessTracing() {}
}
