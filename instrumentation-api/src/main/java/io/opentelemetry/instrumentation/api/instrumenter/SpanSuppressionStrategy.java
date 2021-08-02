/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.Arrays;
import java.util.List;

abstract class SpanSuppressionStrategy {

  private static final SpanSuppressionStrategy SERVER_STRATEGY = new SuppressIfSameType(
      Arrays.asList(SpanKey.SERVER));
  private static final SpanSuppressionStrategy CONSUMER_STRATEGY = new NeverSuppressAndStore(
      Arrays.asList(SpanKey.CONSUMER));

  static SpanSuppressionStrategy from(List<SpanKey> clientSpanKeys) {
    if (clientSpanKeys.isEmpty()) {
      return new CompositeStrategy(
          NeverSuppress.INSTANCE,
          SERVER_STRATEGY,
          CONSUMER_STRATEGY);
    }
    return new CompositeStrategy(
        new SuppressIfSameType(clientSpanKeys),
        SERVER_STRATEGY,
        CONSUMER_STRATEGY);
  }

  abstract Context storeInContext(SpanKind spanKind, Context context, Span span);

  abstract boolean shouldSuppress(SpanKind spanKind, Context parentContext);

  static final class SuppressIfSameType extends SpanSuppressionStrategy {

    private final List<SpanKey> outgoingSpanKeys;

    SuppressIfSameType(List<SpanKey> outgoingSpanKeys) {
      this.outgoingSpanKeys = outgoingSpanKeys;
    }

    @Override
    Context storeInContext(SpanKind spanKind, Context context, Span span) {
      for (SpanKey outgoingSpanKey : outgoingSpanKeys) {
        context = outgoingSpanKey.with(context, span);
      }
      return context;
    }

    @Override
    boolean shouldSuppress(SpanKind spanKind, Context parentContext) {
        for (SpanKey outgoingSpanKey : outgoingSpanKeys) {
          if (outgoingSpanKey.fromContextOrNull(parentContext) == null) {
            return false;
          }
        }
        return true;
    }
  }

  static final class NeverSuppress extends SpanSuppressionStrategy {

    private static final SpanSuppressionStrategy INSTANCE = new NeverSuppress();

    @Override
    Context storeInContext(SpanKind spanKind, Context context, Span span) {
      return context;
    }

    @Override
    boolean shouldSuppress(SpanKind spanKind, Context parentContext) {
      return false;
    }
  }

  static final class NeverSuppressAndStore extends SpanSuppressionStrategy {

    private final List<SpanKey> outgoingSpanKeys;

    NeverSuppressAndStore(List<SpanKey> outgoingSpanKeys) {
      this.outgoingSpanKeys = outgoingSpanKeys;
    }

    @Override
    Context storeInContext(SpanKind spanKind, Context context, Span span) {
      for (SpanKey outgoingSpanKey : outgoingSpanKeys) {
        context = outgoingSpanKey.with(context, span);
      }
      return context;
    }

    @Override
    boolean shouldSuppress(SpanKind spanKind, Context parentContext) {
        return false;
    }
  }

  static final class CompositeStrategy extends SpanSuppressionStrategy {
    private final SpanSuppressionStrategy clientStrategy;
    private final SpanSuppressionStrategy serverStrategy;
    private final SpanSuppressionStrategy consumerStrategy;

    public CompositeStrategy(SpanSuppressionStrategy client, SpanSuppressionStrategy server,
        SpanSuppressionStrategy consumer) {
      this.clientStrategy = client;
      this.serverStrategy = server;
      this.consumerStrategy = consumer;
    }

    @Override
    Context storeInContext(SpanKind spanKind, Context context, Span span) {
      switch (spanKind) {
        case CLIENT:
        case PRODUCER:
          return clientStrategy.storeInContext(spanKind, context, span);
        case SERVER:
          return serverStrategy.storeInContext(spanKind, context, span);
        case CONSUMER:
          return consumerStrategy.storeInContext(spanKind, context, span);
        case INTERNAL:
          return context;
      }
      return context;
    }

    @Override
    boolean shouldSuppress(SpanKind spanKind, Context parentContext) {
      switch (spanKind) {
        case CLIENT:
        case PRODUCER:
          return clientStrategy.shouldSuppress(spanKind, parentContext);
        case SERVER:
          return serverStrategy.shouldSuppress(spanKind, parentContext);
        case CONSUMER:
          return consumerStrategy.shouldSuppress(spanKind, parentContext);
        case INTERNAL:
          return false;
      }
      return false;
    }
  }
}