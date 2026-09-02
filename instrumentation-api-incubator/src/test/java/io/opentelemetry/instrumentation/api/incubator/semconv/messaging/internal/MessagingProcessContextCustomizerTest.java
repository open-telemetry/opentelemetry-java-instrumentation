/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MessagingProcessContextCustomizerTest {

  private static final TextMapGetter<Map<String, String>> getter =
      new TextMapGetter<Map<String, String>>() {
        @Override
        public Iterable<String> keys(Map<String, String> carrier) {
          return carrier.keySet();
        }

        @Override
        public String get(Map<String, String> carrier, String key) {
          return carrier.get(key);
        }
      };

  private static final ContextCustomizer<Map<String, String>> underTest =
      MessagingProcessContextCustomizer.create(W3CTraceContextPropagator.getInstance(), getter);

  @Test
  void preservesAmbientParent() {
    Context ambient = context("11111111111111111111111111111111", "1111111111111111");
    Span ambientSpan = Span.fromContext(ambient);

    Context result = underTest.onStart(ambient, carrier(), Attributes.empty());

    assertThat(Span.fromContext(result)).isSameAs(ambientSpan);
  }

  @Test
  void preservesPropagatedFieldsWithAmbientParent() {
    ContextKey<String> propagatedKey = ContextKey.named("propagated");
    ContextCustomizer<String> customizer =
        MessagingProcessContextCustomizer.create(
            (parent, request) -> parent.with(propagatedKey, request));
    Context ambient = context("11111111111111111111111111111111", "1111111111111111");

    Context result = customizer.onStart(ambient, "value", Attributes.empty());

    assertThat(result.get(propagatedKey)).isEqualTo("value");
    assertThat(Span.fromContext(result).getSpanContext())
        .isEqualTo(Span.fromContext(ambient).getSpanContext());
  }

  @Test
  void usesProducerWhenAmbientParentIsMissing() {
    Context result = underTest.onStart(Context.root(), carrier(), Attributes.empty());

    assertThat(Span.fromContext(result).getSpanContext())
        .isEqualTo(spanContext("22222222222222222222222222222222", "2222222222222222"));
  }

  private static Map<String, String> carrier() {
    Map<String, String> carrier = new HashMap<>();
    carrier.put("traceparent", "00-22222222222222222222222222222222-2222222222222222-01");
    return Collections.unmodifiableMap(carrier);
  }

  private static Context context(String traceId, String spanId) {
    return Context.root().with(Span.wrap(spanContext(traceId, spanId)));
  }

  private static SpanContext spanContext(String traceId, String spanId) {
    return SpanContext.createFromRemoteParent(
        traceId, spanId, TraceFlags.getSampled(), TraceState.getDefault());
  }
}
