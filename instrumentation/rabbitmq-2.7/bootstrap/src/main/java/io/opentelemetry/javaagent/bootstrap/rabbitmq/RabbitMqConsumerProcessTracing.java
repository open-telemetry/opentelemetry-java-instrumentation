/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import io.opentelemetry.instrumentation.api.internal.ScopedThreadSuppression;

/** Coordinates process telemetry between Spring Rabbit and RabbitMQ instrumentations. */
public final class RabbitMqConsumerProcessTracing {

  private static final ScopedThreadSuppression processSpanSuppression =
      new ScopedThreadSuppression();

  public static ScopedThreadSuppression processSpanSuppression() {
    return processSpanSuppression;
  }

  private RabbitMqConsumerProcessTracing() {}
}
