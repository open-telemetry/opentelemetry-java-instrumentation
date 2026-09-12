/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetrySuppression;

/** Coordinates process telemetry between Spring Rabbit and RabbitMQ instrumentations. */
public final class RabbitMqConsumerProcessTracing {

  // This holder is the coordination key, so its suppressed signals stay invisible to every other
  // messaging stack that runs on the same thread.
  private static final MessagingTelemetrySuppression suppression =
      MessagingTelemetrySuppression.create();

  public static MessagingTelemetrySignals suppress(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return suppression.suppress(operation, signal);
  }

  public static void restore(MessagingTelemetrySignals previous) {
    suppression.restore(previous);
  }

  public static boolean isSuppressed(
      MessagingOperationType operation, MessagingTelemetrySignal signal) {
    return suppression.isSuppressed(operation, signal);
  }

  private RabbitMqConsumerProcessTracing() {}
}
