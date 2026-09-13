/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignals;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetrySuppression;
import java.util.function.BooleanSupplier;

// Classes used by multiple instrumentations should be in a bootstrap module to ensure that all
// instrumentations see the same class. Helper classes are injected into each class loader that
// contains an instrumentation that uses them, so instrumentations in different class loaders will
// have separate copies of helper classes.
public final class KafkaClientsConsumerProcessTracing {

  private static final ContextKey<SpanContext> FRAMEWORK_PROCESS_SPAN_KEY =
      ContextKey.named("opentelemetry-kafka-framework-process-span");

  // This holder is the coordination key, so its suppressed signals stay invisible to every other
  // messaging stack that runs on the same thread.
  private static final MessagingTelemetrySuppression suppression =
      MessagingTelemetrySuppression.create();

  public static boolean setWrappingEnabled(boolean enabled) {
    MessagingTelemetrySignals previous = suppression.current();
    suppression.restore(enabled ? previous.without(PROCESS, SPAN) : previous.with(PROCESS, SPAN));
    return !previous.contains(PROCESS, SPAN);
  }

  public static boolean isWrappingEnabled() {
    return !suppression.isSuppressed(PROCESS, SPAN);
  }

  public static BooleanSupplier getWrappingEnabledSupplier() {
    return KafkaClientsConsumerProcessTracing::isWrappingEnabled;
  }

  public static Context markFrameworkProcess(Context context) {
    return context.with(
        FRAMEWORK_PROCESS_SPAN_KEY, Span.fromContext(context).getSpanContext());
  }

  public static Context withoutFrameworkProcess(Context context) {
    SpanContext processSpan = context.get(FRAMEWORK_PROCESS_SPAN_KEY);
    if (processSpan == null || !Span.fromContext(context).getSpanContext().equals(processSpan)) {
      return context;
    }

    return Context.root().with(Span.wrap(processSpan));
  }

  private KafkaClientsConsumerProcessTracing() {}
}
