/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.pekkoremote.v1_0.internal;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class InjectExtractContextRoundtripTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private final Tracer tracer = GlobalOpenTelemetry.getTracer("test");

  private final TextMapSetter<StringBuilder> textMapSetter = StringBuilderTextMapSetter.INSTANCE;
  private final TextMapGetter<String> textMapGetter = StringTextMapGetter.INSTANCE;

  @Test
  void testInjectExtractRoundtrip() {
    StringBuilder carrier = new StringBuilder();

    Baggage baggage =
        Baggage.empty().toBuilder()
            .put("baggage_key_1", "baggage_value")
            .put("baggage_key_2", "#%640=")
            .build();

    TextMapPropagator propagator =
        TextMapPropagator.composite(
            W3CBaggagePropagator.getInstance(), W3CTraceContextPropagator.getInstance());

    Span parentSpan = tracer.spanBuilder("injected").startSpan();
    try (Scope unusedScope = parentSpan.makeCurrent()) {
      try (Scope unusedBaggageScope = baggage.makeCurrent()) {

        propagator.inject(Context.current(), carrier, textMapSetter);
      }
    }

    Context extractedContext =
        propagator.extract(Context.current(), carrier.toString(), textMapGetter);

    Span extractedParentSpan =
        tracer.spanBuilder("extracted").setParent(extractedContext).startSpan();
    assertEquals(
        parentSpan.getSpanContext().getTraceId(),
        extractedParentSpan.getSpanContext().getTraceId());
    assertEquals(
        parentSpan.getSpanContext().getTraceFlags(),
        extractedParentSpan.getSpanContext().getTraceFlags());

    Baggage extractedBaggage = Baggage.fromContext(extractedContext);
    assertIterableEquals(
        baggage.asMap().entrySet().stream().sorted(Map.Entry.comparingByKey()).collect(toList()),
        extractedBaggage.asMap().entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .collect(toList()));
  }
}
