/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetrySuppression;
import java.util.function.BooleanSupplier;

// Classes used by multiple instrumentations should be in a bootstrap module to ensure that all
// instrumentations see the same class. Helper classes are injected into each class loader that
// contains an instrumentation that uses them, so instrumentations in different class loaders will
// have separate copies of helper classes.
public final class KafkaClientsConsumerProcessTracing {

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

  public static BooleanSupplier getProcessSpanEnabledSupplier() {
    return () ->
        !suppression.isSuppressed(MessagingOperationType.PROCESS, MessagingTelemetrySignal.SPAN);
  }

  private KafkaClientsConsumerProcessTracing() {}
}
