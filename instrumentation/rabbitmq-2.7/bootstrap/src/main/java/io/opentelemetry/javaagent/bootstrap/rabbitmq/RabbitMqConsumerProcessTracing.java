/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.rabbitmq;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetrySuppression;

/** Coordinates process telemetry ownership between Spring Rabbit and RabbitMQ instrumentations. */
public final class RabbitMqConsumerProcessTracing {

  // this holder is the coordination key, so the claims made here stay invisible to every other
  // messaging stack that runs on the same thread
  private static final MessagingTelemetrySuppression suppression =
      MessagingTelemetrySuppression.create();

  public static boolean setWrappingEnabled(boolean enabled) {
    MessagingTelemetryClaims previous = suppression.current();
    suppression.restore(enabled ? previous.without(PROCESS, SPAN) : previous.with(PROCESS, SPAN));
    return !previous.contains(PROCESS, SPAN);
  }

  public static boolean isWrappingEnabled() {
    return !suppression.isSuppressed(PROCESS, SPAN);
  }

  private RabbitMqConsumerProcessTracing() {}
}
