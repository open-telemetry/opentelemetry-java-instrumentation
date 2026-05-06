/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.tracing.Tracer;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class LettuceExceptionSanitizationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  // Tracer.Span.start() takes no args in lettuce 5.x, but requires RedisCommand in 6.x+.
  private static void startSpan(Tracer.Span span) {
    try {
      Method startMethod = span.getClass().getMethod("start");
      startMethod.invoke(span);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(e);
    }
  }

  private static final String SENSITIVE_PASSWORD = "mysecretpassword";

  @Test
  void exceptionMessageSanitizedWhenQuerySanitizationEnabled() {
    LettuceTelemetry telemetry =
        LettuceTelemetry.builder(testing.getOpenTelemetry())
            .setQuerySanitizationEnabled(true)
            .build();

    Tracer.Span span = telemetry.createTracing().getTracerProvider().getTracer().nextSpan();
    span.name("AUTH");
    startSpan(span);
    span.error(
        new RedisCommandExecutionException(
            "WRONGPASS invalid username-password pair: " + SENSITIVE_PASSWORD));
    span.finish();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                spanAssert ->
                    spanAssert.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfyingExactly(
                                    equalTo(
                                        EXCEPTION_TYPE,
                                        RedisCommandExecutionException.class.getName()),
                                    satisfies(
                                        EXCEPTION_STACKTRACE,
                                        stack -> stack.doesNotContain(SENSITIVE_PASSWORD)),
                                    equalTo(EXCEPTION_MESSAGE, "<redacted>")))));
  }

  @Test
  void exceptionMessageNotSanitizedWhenQuerySanitizationDisabled() {
    LettuceTelemetry telemetry =
        LettuceTelemetry.builder(testing.getOpenTelemetry())
            .setQuerySanitizationEnabled(false)
            .build();

    String sensitiveMsg = "WRONGPASS invalid username-password pair: " + SENSITIVE_PASSWORD;

    Tracer.Span span = telemetry.createTracing().getTracerProvider().getTracer().nextSpan();
    span.name("AUTH");
    startSpan(span);
    span.error(new RedisCommandExecutionException(sensitiveMsg));
    span.finish();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                spanAssert ->
                    spanAssert.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfyingExactly(
                                    equalTo(
                                        EXCEPTION_TYPE,
                                        RedisCommandExecutionException.class.getName()),
                                    satisfies(
                                        EXCEPTION_STACKTRACE,
                                        stack -> stack.isInstanceOf(String.class)),
                                    satisfies(
                                        EXCEPTION_MESSAGE,
                                        msg -> msg.contains(SENSITIVE_PASSWORD))))));
  }

  @Test
  void nonRedisCommandExecutionExceptionUnaffected() {
    LettuceTelemetry telemetry =
        LettuceTelemetry.builder(testing.getOpenTelemetry())
            .setQuerySanitizationEnabled(true)
            .build();

    Tracer.Span span = telemetry.createTracing().getTracerProvider().getTracer().nextSpan();
    span.name("GET");
    startSpan(span);
    span.error(new RuntimeException("boom"));
    span.finish();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                spanAssert ->
                    spanAssert.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfyingExactly(
                                    equalTo(EXCEPTION_TYPE, RuntimeException.class.getName()),
                                    satisfies(
                                        EXCEPTION_STACKTRACE,
                                        stack -> stack.isInstanceOf(String.class)),
                                    equalTo(EXCEPTION_MESSAGE, "boom")))));
  }
}
