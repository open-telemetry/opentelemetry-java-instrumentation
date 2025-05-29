/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.api.trace.SpanKind.PRODUCER;
import static io.opentelemetry.api.trace.StatusCode.ERROR;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TracerTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @DisplayName("capture span, kind, attributes, and status")
  void captureSpanKindAttributesAndStatus() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan();
    testSpan.setAttribute("string", "1");
    testSpan.setAttribute("long", 2L);
    testSpan.setAttribute("double", 3.0);
    testSpan.setAttribute("boolean", true);
    testSpan.setStatus(ERROR);
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasKind(PRODUCER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(stringKey("string"), "1"),
                            equalTo(longKey("long"), 2L),
                            equalTo(doubleKey("double"), 3.0),
                            equalTo(booleanKey("boolean"), true))));
  }

  @Test
  @DisplayName("capture span with implicit parent using Tracer.withSpan()")
  void captureSpanWithImplicitParentUsingTracerWithSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span parentSpan = tracer.spanBuilder("parent").startSpan();
    Scope parentScope = Context.current().with(parentSpan).makeCurrent();

    Span testSpan = tracer.spanBuilder("test").startSpan();
    testSpan.end();

    parentSpan.end();
    parentScope.close();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("test").hasParent(trace.getSpan(0)).hasTotalAttributeCount(0)));
  }

  @Test
  @DisplayName("capture span with implicit parent using makeCurrent")
  void captureSpanWithImplicitParentUsingMakeCurrent() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span parentSpan = tracer.spanBuilder("parent").startSpan();
    Scope parentScope = parentSpan.makeCurrent();

    Span testSpan = tracer.spanBuilder("test").startSpan();
    testSpan.end();

    parentSpan.end();
    parentScope.close();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("test").hasParent(trace.getSpan(0)).hasTotalAttributeCount(0)));
  }

  @Test
  @DisplayName(
      "capture span with implicit parent using TracingContextUtils.withSpan and makeCurrent")
  void captureSpanWithImplicitParentUsingTracingContextUtilsWithSpanAndMakeCurrent() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span parentSpan = tracer.spanBuilder("parent").startSpan();
    Context parentContext = Context.current().with(parentSpan);
    Scope parentScope = parentContext.makeCurrent();

    Span testSpan = tracer.spanBuilder("test").startSpan();
    testSpan.end();

    parentSpan.end();
    parentScope.close();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("test").hasParent(trace.getSpan(0)).hasTotalAttributeCount(0)));
  }

  @Test
  @DisplayName("capture span with explicit parent")
  void captureSpanWithExplicitParent() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span parentSpan = tracer.spanBuilder("parent").startSpan();
    Context context = Context.root().with(parentSpan);
    Span testSpan = tracer.spanBuilder("test").setParent(context).startSpan();
    testSpan.end();
    parentSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("test").hasParent(trace.getSpan(0)).hasTotalAttributeCount(0)));
  }

  @Test
  @DisplayName("capture span with explicit no parent")
  void captureSpanWithExplicitNoParent() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span parentSpan = tracer.spanBuilder("parent").startSpan();
    Scope parentScope = parentSpan.makeCurrent();
    Span testSpan = tracer.spanBuilder("test").setNoParent().startSpan();
    testSpan.end();
    parentSpan.end();
    parentScope.close();

    // Then
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", "test"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0)),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasNoParent().hasTotalAttributeCount(0)));
  }

  @Test
  @DisplayName("capture name update")
  void captureNameUpdate() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    testSpan.updateName("test2");
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test2").hasNoParent().hasTotalAttributeCount(0)));
  }

  @Test
  @DisplayName("capture exception")
  void captureException() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    IllegalStateException throwable = new IllegalStateException();
    testSpan.recordException(throwable);
    testSpan.end();

    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test").hasTotalAttributeCount(0).hasException(throwable)));
  }

  @Test
  @DisplayName("capture exception with Attributes")
  void captureExceptionWithAttributes() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    IllegalStateException throwable = new IllegalStateException();
    testSpan.recordException(throwable, Attributes.builder().put("dog", "bark").build());
    testSpan.end();

    StringWriter writer = new StringWriter();
    throwable.printStackTrace(new PrintWriter(writer));

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasTotalAttributeCount(0)
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, "java.lang.IllegalStateException"),
                                        equalTo(EXCEPTION_STACKTRACE, writer.toString()),
                                        equalTo(stringKey("dog"), "bark")))));
  }

  @Test
  @DisplayName("capture name update using TracingContextUtils.getCurrentSpan()")
  void captureNameUpdateUsingTracingContextUtilsGetCurrentSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    Scope testScope = Context.current().with(testSpan).makeCurrent();
    Span.current().updateName("test2");
    testScope.close();
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test2").hasNoParent().hasTotalAttributeCount(0)));
  }

  @Test
  @DisplayName("capture name update using TracingContextUtils.Span.fromContext(Context.current())")
  void captureNameUpdateUsingTracingContextUtilsSpanFromContextCurrent() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    Scope testScope = Context.current().with(testSpan).makeCurrent();
    Span.fromContext(Context.current()).updateName("test2");
    testScope.close();
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test2").hasNoParent().hasTotalAttributeCount(0)));
  }

  @Test
  @DisplayName("add wrapped span to context")
  void addWrappedSpanToContext() {
    // When
    // Lazy way to get a span context
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan();
    testSpan.end();

    Span span = Span.wrap(testSpan.getSpanContext());
    Context context = Context.current().with(span);

    // Then
    assertThat(Span.fromContext(context).getSpanContext().getSpanId())
        .isEqualTo(span.getSpanContext().getSpanId());
  }

  // this test uses opentelemetry-api-1.4 instrumentation
  @Test
  @DisplayName("test tracer builder")
  void testTracerBuilder() {
    // When
    Tracer tracer =
        GlobalOpenTelemetry.get().tracerBuilder("test").setInstrumentationVersion("1.2.3").build();
    Span testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan();
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("test")
                        .hasKind(PRODUCER)
                        .hasNoParent()
                        .hasTotalAttributeCount(0)
                        .hasInstrumentationScopeInfo(
                            InstrumentationScopeInfo.builder("test").setVersion("1.2.3").build())));
  }
}
