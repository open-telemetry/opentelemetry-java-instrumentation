/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.Collections;
import java.util.List;

abstract class SpanSuppressionStrategy {

  private static final SpanSuppressionStrategy SERVER_STRATEGY = new SuppressIfSameType(
      Collections.singletonList(SpanKey.SERVER));
  private static final SpanSuppressionStrategy CONSUMER_STRATEGY = new NeverSuppressAndStore(
      Collections.singletonList(SpanKey.CONSUMER));

  private static final SpanSuppressionStrategy NO_CLIENT_SUPPRESSION_STRATEGY = new CompositeStrategy(
      NeverSuppress.INSTANCE,
      SERVER_STRATEGY,
      CONSUMER_STRATEGY);

  static SpanSuppressionStrategy from(List<SpanKey> clientSpanKeys) {
    if (clientSpanKeys.isEmpty()) {
      return NO_CLIENT_SUPPRESSION_STRATEGY;
    }

    return new CompositeStrategy(
        new SuppressIfSameType(clientSpanKeys),
        SERVER_STRATEGY,
        CONSUMER_STRATEGY);
  }

  abstract Context storeInContext(Context context, SpanKind spanKind, Span span);

  abstract boolean shouldSuppress(Context parentContext, SpanKind spanKind);

  static final class SuppressIfSameType extends SpanSuppressionStrategy {

    private final List<SpanKey> outgoingSpanKeys;

    SuppressIfSameType(List<SpanKey> outgoingSpanKeys) {
      this.outgoingSpanKeys = outgoingSpanKeys;
    }

    @Override
    Context storeInContext(Context context, SpanKind spanKind, Span span) {
      for (SpanKey outgoingSpanKey : outgoingSpanKeys) {
        context = outgoingSpanKey.with(context, span);
      }
      return context;
    }

    @Override
    boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
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
    Context storeInContext(Context context, SpanKind spanKind, Span span) {
      return context;
    }

    @Override
    boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
      return false;
    }
  }

  static final class NeverSuppressAndStore extends SpanSuppressionStrategy {

    private final List<SpanKey> outgoingSpanKeys;

    NeverSuppressAndStore(List<SpanKey> outgoingSpanKeys) {
      this.outgoingSpanKeys = outgoingSpanKeys;
    }

    @Override
    Context storeInContext(Context context, SpanKind spanKind, Span span) {
      for (SpanKey outgoingSpanKey : outgoingSpanKeys) {
        context = outgoingSpanKey.with(context, span);
      }
      return context;
    }

    @Override
    boolean shouldSuppress(Context parentContext, SpanKind spanKind) {
        return false;
    }
  }

  static final class CompositeStrategy extends SpanSuppressionStrategy {
    private final SpanSuppressionStrategy clientStrategy;
    private final SpanSuppressionStrategy serverStrategy;
    private final SpanSuppressionStrategy consumerStrategy;

    CompositeStrategy(SpanSuppressionStrategy client, SpanSuppressionStrategy server,
        SpanSuppressionStrategy consumer) {
      this.clientStrategy = client;
      this.serverStrategy = server;
      this.consumerStrategy = consumer;
    }

    @Override
    Context storeInContext(Context context, SpanKind spanKind, Span span) {
      switch (spanKind) {
        case CLIENT:
        case PRODUCER:
          return clientStrategy.storeInContext(context, spanKind, span);
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
        case PRODUCER:
          return clientStrategy.shouldSuppress(parentContext, spanKind);
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
}