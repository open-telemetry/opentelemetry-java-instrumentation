/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Collections.singleton;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import java.util.Set;

abstract class SpanSuppressionStrategy {
  private static final SpanSuppressionStrategy SERVER_STRATEGY =
      new SuppressIfSameSpanKeyStrategy(singleton(SpanKey.SERVER));
  private static final SpanSuppressionStrategy ALL_CLIENTS_STRATEGY =
      new SuppressIfSameSpanKeyStrategy(singleton(SpanKey.ALL_CLIENTS));

  private static final SpanSuppressionStrategy SUPPRESS_GENERIC_CLIENTS_AND_SERVERS =
      new CompositeSuppressionStrategy(
          ALL_CLIENTS_STRATEGY,
          NoopSuppressionStrategy.INSTANCE,
          SERVER_STRATEGY,
          NoopSuppressionStrategy.INSTANCE);

  private static final SpanSuppressionStrategy SUPPRESS_ONLY_SERVERS =
      new CompositeSuppressionStrategy(
          NoopSuppressionStrategy.INSTANCE,
          NoopSuppressionStrategy.INSTANCE,
          SERVER_STRATEGY,
          NoopSuppressionStrategy.INSTANCE);

  static SpanSuppressionStrategy suppressNestedClients(Set<SpanKey> spanKeys) {
    if (spanKeys.isEmpty()) {
      return SUPPRESS_GENERIC_CLIENTS_AND_SERVERS;
    }

    SpanSuppressionStrategy spanKeyStrategy = new SuppressIfSameSpanKeyStrategy(spanKeys);
    return new CompositeSuppressionStrategy(
        ALL_CLIENTS_STRATEGY, spanKeyStrategy, SERVER_STRATEGY, spanKeyStrategy);
  }

  static SpanSuppressionStrategy from(Set<SpanKey> spanKeys) {
    if (spanKeys.isEmpty()) {
      return SUPPRESS_ONLY_SERVERS;
    }

    SpanSuppressionStrategy spanKeyStrategy = new SuppressIfSameSpanKeyStrategy(spanKeys);
    return new CompositeSuppressionStrategy(
        spanKeyStrategy, spanKeyStrategy, SERVER_STRATEGY, spanKeyStrategy);
  }

  abstract Context storeInContext(Context context, SpanKind spanKind, Span span);

  abstract boolean shouldSuppress(Context parentContext, SpanKind spanKind);
}
