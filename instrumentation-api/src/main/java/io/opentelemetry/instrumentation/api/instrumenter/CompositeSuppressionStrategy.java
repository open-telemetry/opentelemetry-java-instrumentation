/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;

final class CompositeSuppressionStrategy extends SpanSuppressionStrategy {
  private final SpanSuppressionStrategy clientStrategy;
  private final SpanSuppressionStrategy producerStrategy;
  private final SpanSuppressionStrategy serverStrategy;
  private final SpanSuppressionStrategy consumerStrategy;

  CompositeSuppressionStrategy(
      SpanSuppressionStrategy client,
      SpanSuppressionStrategy producer,
      SpanSuppressionStrategy server,
      SpanSuppressionStrategy consumer) {
    this.clientStrategy = client;
    this.producerStrategy = producer;
    this.serverStrategy = server;
    this.consumerStrategy = consumer;
  }

  @Override
  Context storeInContext(Context context, SpanKind spanKind, Span span) {
    switch (spanKind) {
      case CLIENT:
        return clientStrategy.storeInContext(context, spanKind, span);
      case PRODUCER:
        return producerStrategy.storeInContext(context, spanKind, span);
      case SERVER:
        return serverStrategy.storeInContext(context, spanKind, span);
      case CONSUMER:
        return consumerStrategy.storeInContext(context, spanKind, span);
      case INTERNAL:
        return context;
    }
    return context;
  }

  @Override
  boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
    switch (spanKind) {
      case CLIENT:
        return clientStrategy.shouldSuppress(parentContext, spanKind);
      case PRODUCER:
        return producerStrategy.shouldSuppress(parentContext, spanKind);
      case SERVER:
        return serverStrategy.shouldSuppress(parentContext, spanKind);
      case CONSUMER:
        return consumerStrategy.shouldSuppress(parentContext, spanKind);
      case INTERNAL:
        return false;
    }
    return false;
  }
}
