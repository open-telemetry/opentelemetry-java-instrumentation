/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Collections.singleton;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.Set;

abstract class SpanSuppressionStrategy {
  private static final SpanSuppressionStrategy SERVER_STRATEGY =
      new SuppressIfSameSpanKeyStrategy(singleton(SpanKey.SERVER));
  private static final SpanSuppressionStrategy CONSUMER_STRATEGY =
      new SuppressIfSameSpanKeyStrategy(singleton(SpanKey.CONSUMER));
  private static final SpanSuppressionStrategy ALL_CLIENTS_STRATEGY =
      new SuppressIfSameSpanKeyStrategy(singleton(SpanKey.ALL_CLIENTS));
  private static final SpanSuppressionStrategy ALL_PRODUCERS_STRATEGY =
      new SuppressIfSameSpanKeyStrategy(singleton(SpanKey.ALL_PRODUCERS));

  public static final SpanSuppressionStrategy SUPPRESS_ALL_NESTED_OUTGOING_STRATEGY =
      new CompositeSuppressionStrategy(
          ALL_CLIENTS_STRATEGY, ALL_PRODUCERS_STRATEGY, SERVER_STRATEGY, CONSUMER_STRATEGY);

  private static final SpanSuppressionStrategy NO_CLIENT_SUPPRESSION_STRATEGY =
      new CompositeSuppressionStrategy(
          NoopSuppressionStrategy.INSTANCE,
          NoopSuppressionStrategy.INSTANCE,
          SERVER_STRATEGY,
          CONSUMER_STRATEGY);

  static SpanSuppressionStrategy from(Set<SpanKey> clientSpanKeys) {
    if (clientSpanKeys.isEmpty()) {
      return NO_CLIENT_SUPPRESSION_STRATEGY;
    }

    SpanSuppressionStrategy clientOrProducerStrategy =
        new SuppressIfSameSpanKeyStrategy(clientSpanKeys);
    return new CompositeSuppressionStrategy(
        clientOrProducerStrategy, clientOrProducerStrategy, SERVER_STRATEGY, CONSUMER_STRATEGY);
  }

  abstract Context storeInContext(Context context, SpanKind spanKind, Span span);

  abstract boolean shouldSuppress(Context parentContext, SpanKind spanKind);
}
