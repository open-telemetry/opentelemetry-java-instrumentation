/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.integration.v4_1;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;

final class SpringIntegrationConsumerMetrics implements OperationListener {

  private static final ContextKey<Context> METRICS_CONTEXT =
      ContextKey.named("spring-integration-consumer-metrics");

  private final OperationListener processDuration;
  private final OperationListener consumedMessages;

  SpringIntegrationConsumerMetrics(Meter meter) {
    processDuration = MessagingProcessMetrics.get().create(meter);
    consumedMessages = MessagingConsumerMetrics.getConsumedMessages().create(meter);
  }

  @Override
  public Context onStart(Context context, Attributes startAttributes, long startNanos) {
    Context metricsContext = Context.root().with(Span.fromContext(context));
    metricsContext = processDuration.onStart(metricsContext, startAttributes, startNanos);
    metricsContext = consumedMessages.onStart(metricsContext, startAttributes, startNanos);
    return context.with(METRICS_CONTEXT, metricsContext);
  }

  @Override
  public void onEnd(Context context, Attributes endAttributes, long endNanos) {
    Context metricsContext = context.get(METRICS_CONTEXT);
    if (metricsContext == null) {
      return;
    }
    processDuration.onEnd(metricsContext, endAttributes, endNanos);
    consumedMessages.onEnd(metricsContext, endAttributes, endNanos);
  }
}
