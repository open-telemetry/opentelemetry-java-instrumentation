/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpclient.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapPropagator;
import java.net.http.HttpHeaders;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class HttpHeadersSetterTest {

  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
  private static final String SPAN_ID = "b7ad6b7169203331";
  private static final String EXPECTED_TRACEPARENT = "00-" + TRACE_ID + "-" + SPAN_ID + "-01";

  @ParameterizedTest
  @MethodSource("existingHeaderNames")
  void replacesExistingPropagationHeadersRegardlessOfCase(
      String traceparentHeaderName, String baggageHeaderName) {
    HttpHeadersSetter setter =
        new HttpHeadersSetter(
            ContextPropagators.create(
                TextMapPropagator.composite(
                    W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance())));

    SpanContext spanContext =
        SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), TraceState.getDefault());
    Context context =
        Context.root()
            .with(Span.wrap(spanContext))
            .with(Baggage.builder().put("user", "alice").build());

    HttpHeaders original =
        HttpHeaders.of(
            Map.of(
                traceparentHeaderName,
                List.of("00-11111111111111111111111111111111-2222222222222222-01"),
                baggageHeaderName,
                List.of("old=value"),
                "X-Custom",
                List.of("custom-value")),
            (name, value) -> true);

    HttpHeaders injected = setter.inject(original, context);

    assertThat(injected.map())
        .containsEntry("traceparent", List.of(EXPECTED_TRACEPARENT))
        .containsEntry("baggage", List.of("user=alice"))
        .containsEntry("X-Custom", List.of("custom-value"))
        .hasSize(3);
    assertThat(List.copyOf(injected.map().keySet()))
        .containsExactlyInAnyOrder("traceparent", "baggage", "X-Custom");
  }

  private static Stream<Arguments> existingHeaderNames() {
    return Stream.of(
        argumentSet("lowercase", "traceparent", "baggage"),
        argumentSet("capitalized", "Traceparent", "Baggage"),
        argumentSet("mixed case", "TraceParent", "BaGgAgE"),
        argumentSet("uppercase", "TRACEPARENT", "BAGGAGE"));
  }
}
