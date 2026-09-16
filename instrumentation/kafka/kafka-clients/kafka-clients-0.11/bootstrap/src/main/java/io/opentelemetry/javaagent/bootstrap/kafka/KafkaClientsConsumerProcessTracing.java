/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.kafka;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType.PROCESS;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetrySignal.SPAN;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.impl.InstrumentationUtil;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetrySuppression;
import java.util.function.BooleanSupplier;

// Classes used by multiple instrumentations should be in a bootstrap module to ensure that all
// instrumentations see the same class. Helper classes are injected into each class loader that
// contains an instrumentation that uses them, so instrumentations in different class loaders will
// have separate copies of helper classes.
public final class KafkaClientsConsumerProcessTracing {

  private static final ContextKey<Boolean> FRAMEWORK_PROCESS_KEY =
      ContextKey.named("opentelemetry-kafka-framework-process-span");

  // This holder is the coordination key, so its suppressed signals stay invisible to every other
  // messaging stack that runs on the same thread.
  private static final MessagingTelemetrySuppression currentProcessSpanSuppression =
      MessagingTelemetrySuppression.create();

  public static MessagingTelemetrySuppression currentProcessSpanSuppression() {
    return currentProcessSpanSuppression;
  }

  public static boolean isWrappingEnabled() {
    return !currentProcessSpanSuppression().isSuppressed(PROCESS, SPAN);
  }

  public static BooleanSupplier getWrappingEnabledSupplier() {
    return KafkaClientsConsumerProcessTracing::isWrappingEnabled;
  }

  public static Context markFrameworkProcess(Context context) {
    return context.with(FRAMEWORK_PROCESS_KEY, true);
  }

  public static Context withoutFrameworkProcessSuppression(Context context) {
    if (!Boolean.TRUE.equals(context.get(FRAMEWORK_PROCESS_KEY))
        || InstrumentationUtil.shouldSuppressInstrumentation(context)) {
      return context;
    }

    Context parentContext = Context.root().with(Span.fromContext(context));
    return Baggage.fromContext(context).storeInContext(parentContext);
  }

  private KafkaClientsConsumerProcessTracing() {}
}
