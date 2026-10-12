/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracer;
import io.opentelemetry.instrumentation.lettuce.v5_1.internal.Experimental;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LettuceTelemetryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void encodingEventsDisabledByDefault() {
    recordSpan(LettuceTelemetry.create(testing.getOpenTelemetry()));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasEventsSatisfyingExactly(event -> event.hasName("redis.dispatch"))));
  }

  @Test
  void encodingEventsEnabled() {
    LettuceTelemetryBuilder builder = LettuceTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setEncodingSpanEventsEnabled(builder, true);

    recordSpan(builder.build());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event -> event.hasName("redis.encode.start"),
                        event -> event.hasName("redis.encode.end"),
                        event -> event.hasName("redis.dispatch"))));
  }

  @Test
  void encodingEventsDisabledExplicitly() {
    LettuceTelemetryBuilder builder = LettuceTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setEncodingSpanEventsEnabled(builder, true);
    Experimental.setEncodingSpanEventsEnabled(builder, false);

    recordSpan(builder.build());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasEventsSatisfyingExactly(event -> event.hasName("redis.dispatch"))));
  }

  private static void recordSpan(LettuceTelemetry telemetry) {
    Tracer.Span span = telemetry.createTracing().getTracerProvider().getTracer().nextSpan();
    span.annotate("redis.encode.start");
    span.name("GET").start();
    span.annotate("redis.encode.end");
    span.annotate("redis.dispatch");
    span.finish();
  }
}
