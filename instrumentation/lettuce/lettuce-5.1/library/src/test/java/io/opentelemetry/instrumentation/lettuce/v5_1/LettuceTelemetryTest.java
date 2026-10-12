/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.tracing.Tracer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LettuceTelemetryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void filtersEncodingAnnotations(boolean eventsBeforeStart) {
    LettuceTelemetry telemetry = LettuceTelemetry.create(testing.getOpenTelemetry());
    Tracer.Span span = telemetry.createTracing().getTracerProvider().getTracer().nextSpan();
    span.name("GET");
    if (!eventsBeforeStart) {
      span.start();
    }
    span.annotate("redis.encode.start");
    span.annotate("redis.encode.end");
    span.annotate("redis.encode.custom");
    span.annotate("redis.dispatch");
    if (eventsBeforeStart) {
      span.start();
    }
    span.annotate("redis.encode.start");
    span.annotate("redis.encode.end");
    span.annotate("redis.complete");
    span.finish();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                spanAssert ->
                    spanAssert.hasEventsSatisfyingExactly(
                        event -> event.hasName("redis.dispatch"),
                        event -> event.hasName("redis.complete"))));
  }
}
