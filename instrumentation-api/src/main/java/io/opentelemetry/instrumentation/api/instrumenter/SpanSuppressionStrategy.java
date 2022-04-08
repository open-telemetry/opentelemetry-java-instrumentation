/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static java.util.Collections.singleton;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.instrumenter.SpanSuppressor.BySpanKey;
import io.opentelemetry.instrumentation.api.instrumenter.SpanSuppressor.DelegateBySpanKind;
import io.opentelemetry.instrumentation.api.instrumenter.SpanSuppressor.JustStoreServer;
import io.opentelemetry.instrumentation.api.instrumenter.SpanSuppressor.Noop;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

enum SpanSuppressionStrategy {
  /**
   * Do not suppress spans at all.
   *
   * <p>This strategy will mark spans of {@link SpanKind#SERVER SERVER} kind in the context, so that
   * they can be accessed using {@code ServerSpan}, but will not suppress server spans.
   */
  NONE {
    @Override
    SpanSuppressor create(Set<SpanKey> spanKeys) {
      return JustStoreServer.INSTANCE;
    }
  },
  /**
   * Suppress spans by {@link SpanKind}. This is equivalent to the "legacy" suppression strategy
   * used in the agent.
   *
   * <p>Child spans of the same kind will be suppressed; e.g. if there already is a {@link
   * SpanKind#CLIENT CLIENT} span in the context, a second {@code CLIENT} span won't be started.
   */
  SPAN_KIND {
    @SuppressWarnings("ImmutableEnumChecker") // this field actually is immutable
    private final SpanSuppressor strategy;

    {
      Map<SpanKind, SpanSuppressor> delegates = new EnumMap<>(SpanKind.class);
      delegates.put(SpanKind.SERVER, new BySpanKey(singleton(SpanKey.KIND_SERVER)));
      delegates.put(SpanKind.CLIENT, new BySpanKey(singleton(SpanKey.KIND_CLIENT)));
      delegates.put(SpanKind.CONSUMER, new BySpanKey(singleton(SpanKey.KIND_CONSUMER)));
      delegates.put(SpanKind.PRODUCER, new BySpanKey(singleton(SpanKey.KIND_PRODUCER)));
      strategy = new DelegateBySpanKind(delegates);
    }

    @Override
    SpanSuppressor create(Set<SpanKey> spanKeys) {
      return strategy;
    }
  },
  /**
   * Suppress spans by the semantic convention they're supposed to represent. This strategy uses
   * {@linkplain SpanKey span keys} returned by the {@link SpanKeyProvider#internalGetSpanKey()}
   * method to determine if the span can be created or not. An {@link AttributesExtractor} can
   * implement that method to associate itself (and the {@link Instrumenter} it is a part of) with a
   * particular convention.
   *
   * <p>For example, nested HTTP client spans will be suppressed; but an RPC client span will not
   * suppress an HTTP client span, if the instrumented RPC client uses HTTP as transport.
   */
  SEMCONV {
    @Override
    SpanSuppressor create(Set<SpanKey> spanKeys) {
      if (spanKeys.isEmpty()) {
        return Noop.INSTANCE;
      }
      return new BySpanKey(spanKeys);
    }
  };

  abstract SpanSuppressor create(Set<SpanKey> spanKeys);

  static SpanSuppressionStrategy fromConfig(Config config) {
    String value =
        config.getString("otel.instrumentation.experimental.span-suppression-strategy", "semconv");
    switch (value.toLowerCase(Locale.ROOT)) {
      case "none":
        return NONE;
      case "span-kind":
        return SPAN_KIND;
      default:
        return SEMCONV;
    }
  }
}
