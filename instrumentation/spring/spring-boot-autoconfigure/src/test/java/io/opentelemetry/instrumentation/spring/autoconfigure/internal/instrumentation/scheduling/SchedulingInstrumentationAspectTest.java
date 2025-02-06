/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.scheduling;

import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_FUNCTION;
import static io.opentelemetry.semconv.incubating.CodeIncubatingAttributes.CODE_NAMESPACE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.Schedules;

@SuppressWarnings("deprecation") // using deprecated semconv
class SchedulingInstrumentationAspectTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private InstrumentationSchedulingTester schedulingTester;
  private String unproxiedTesterSimpleClassName;
  private String unproxiedTesterClassName;

  SpringSchedulingInstrumentationAspect newAspect(
      OpenTelemetry openTelemetry, ConfigProperties configProperties) {
    return new SpringSchedulingInstrumentationAspect(openTelemetry, configProperties);
  }

  @BeforeEach
  void setup() {
    InstrumentationSchedulingTester unproxiedTester =
        new InstrumentationSchedulingTester(testing.getOpenTelemetry());
    unproxiedTesterSimpleClassName = unproxiedTester.getClass().getSimpleName();
    unproxiedTesterClassName = unproxiedTester.getClass().getName();

    AspectJProxyFactory factory = new AspectJProxyFactory();
    factory.setTarget(unproxiedTester);

    SpringSchedulingInstrumentationAspect aspect =
        newAspect(
            testing.getOpenTelemetry(),
            DefaultConfigProperties.createFromMap(Collections.emptyMap()));
    factory.addAspect(aspect);

    schedulingTester = factory.getProxy();
  }

  @Test
  @DisplayName("when method is annotated with @Scheduled should start a new span.")
  void scheduled() {
    // when
    schedulingTester.testScheduled();

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(unproxiedTesterSimpleClassName + ".testScheduled")
                        .hasKind(INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                            equalTo(CODE_FUNCTION, "testScheduled"))));
  }

  @Test
  @DisplayName("when method is annotated with multiple @Scheduled should start a new span.")
  void multiScheduled() {
    // when
    schedulingTester.testMultiScheduled();

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(unproxiedTesterSimpleClassName + ".testMultiScheduled")
                        .hasKind(INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                            equalTo(CODE_FUNCTION, "testMultiScheduled"))));
  }

  @Test
  @DisplayName("when method is annotated with @Schedules should start a new span.")
  void schedules() {
    // when
    schedulingTester.testSchedules();

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(unproxiedTesterSimpleClassName + ".testSchedules")
                        .hasKind(INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                            equalTo(CODE_FUNCTION, "testSchedules"))));
  }

  @Test
  @DisplayName(
      "when method is annotated with @Scheduled and it starts nested span, spans should be nested.")
  void nestedSpanInScheduled() {
    // when
    schedulingTester.testNestedSpan();

    // then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(unproxiedTesterSimpleClassName + ".testNestedSpan")
                        .hasKind(INTERNAL)
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                            equalTo(CODE_FUNCTION, "testNestedSpan")),
                nestedSpan ->
                    nestedSpan.hasParent(trace.getSpan(0)).hasKind(INTERNAL).hasName("test")));
  }

  @Test
  @DisplayName(
      "when method is annotated with @Scheduled AND an exception is thrown span should record the exception")
  void scheduledError() {
    assertThatThrownBy(() -> schedulingTester.testScheduledWithException())
        .isInstanceOf(Exception.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(unproxiedTesterSimpleClassName + ".testScheduledWithException")
                        .hasKind(INTERNAL)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(CODE_NAMESPACE, unproxiedTesterClassName),
                            equalTo(CODE_FUNCTION, "testScheduledWithException"))));
  }

  static class InstrumentationSchedulingTester {
    private final OpenTelemetry openTelemetry;

    InstrumentationSchedulingTester(OpenTelemetry openTelemetry) {
      this.openTelemetry = openTelemetry;
    }

    @Scheduled(fixedRate = 1L)
    public void testScheduled() {
      // no-op
    }

    @Scheduled(fixedRate = 1L)
    @Scheduled(fixedRate = 2L)
    public void testMultiScheduled() {
      // no-op
    }

    @Schedules({@Scheduled(fixedRate = 1L), @Scheduled(fixedRate = 2L)})
    public void testSchedules() {
      // no-op
    }

    @Scheduled(fixedRate = 1L)
    public void testNestedSpan() {
      Context current = Context.current();
      Tracer tracer = openTelemetry.getTracer("test");
      try (Scope ignored = current.makeCurrent()) {
        Span span = tracer.spanBuilder("test").startSpan();
        span.end();
      }
    }

    @Scheduled(fixedRate = 1L)
    public void testScheduledWithException() {
      throw new IllegalStateException("something went wrong");
    }
  }
}
