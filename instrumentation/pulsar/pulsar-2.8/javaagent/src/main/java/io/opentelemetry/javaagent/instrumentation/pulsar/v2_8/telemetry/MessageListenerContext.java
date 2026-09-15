/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8.telemetry;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.RECEIVE;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetrySuppression;

/**
 * Helper class used to determine whether message is going to be processed by a listener. If we know
 * that message is going to be passed to a message listener, that would produce a span for the
 * "process" operation, we are going to suppress the span from the message "receive" operation.
 */
public class MessageListenerContext {
  private static final MessagingTelemetrySuppression suppression =
      MessagingTelemetrySuppression.create();

  private MessageListenerContext() {}

  /** Call on entry to a method that will pass the received message to a message listener. */
  public static MessagingTelemetrySignals startProcessing() {
    return suppression.suppress(RECEIVE, SPAN);
  }

  public static void endProcessing(MessagingTelemetrySignals previous) {
    suppression.restore(previous);
  }

  /** Returns true if we expect a received message to be passed to a listener. */
  public static boolean isProcessing() {
    return suppression.isSuppressed(RECEIVE, SPAN);
  }
}
