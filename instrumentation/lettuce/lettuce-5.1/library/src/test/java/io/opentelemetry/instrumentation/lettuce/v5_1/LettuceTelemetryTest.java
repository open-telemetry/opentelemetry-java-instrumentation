/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.RedisCommand;
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
  void filtersEncodingAnnotations(boolean eventsBeforeStart) throws ReflectiveOperationException {
    LettuceTelemetry telemetry = LettuceTelemetry.create(testing.getOpenTelemetry());
    Tracer.Span span = telemetry.createTracing().getTracerProvider().getTracer().nextSpan();
    span.name("GET");
    if (!eventsBeforeStart) {
      startSpan(span);
    }
    span.annotate("redis.encode.start");
    span.annotate("redis.encode.end");
    span.annotate("redis.encode.custom");
    span.annotate("redis.dispatch");
    if (eventsBeforeStart) {
      startSpan(span);
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

  private static void startSpan(Tracer.Span span) throws ReflectiveOperationException {
    try {
      Tracer.Span.class.getMethod("start").invoke(span);
    } catch (NoSuchMethodException ignored) {
      // Newer Lettuce versions require the command when starting a span.
      Tracer.Span.class
          .getMethod("start", RedisCommand.class)
          .invoke(span, new Command<>(CommandType.GET, null));
    }
  }
}
