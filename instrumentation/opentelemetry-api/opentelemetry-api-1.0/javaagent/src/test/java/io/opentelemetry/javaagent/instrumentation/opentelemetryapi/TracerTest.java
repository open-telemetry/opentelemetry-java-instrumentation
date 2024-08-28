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
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import io.opentelemetry.semconv.ExceptionAttributes;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;

@ExtendWith(AgentInstrumentationExtension.class)
public class TracerTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  @DisplayName("capture span, kind, attributes, and status")
  public void captureSpanKindAttributesAndStatus() {
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
  public void captureSpanWithImplicitParentUsingTracerWithSpan() {
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
                span0 -> span0.hasName("parent").hasNoParent().hasAttributesSatisfyingExactly(),
                span1 ->
                    span1
                        .hasName("test")
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly()));
  }

  @Test
  @DisplayName("capture span with implicit parent using makeCurrent")
  public void captureSpanWithImplicitParentUsingMakeCurrent() {
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
                span0 -> span0.hasName("parent").hasNoParent(),
                span1 -> span1.hasName("test").hasParent(trace.getSpan(0))));
  }

  @Test
  @DisplayName(
      "capture span with implicit parent using TracingContextUtils.withSpan and makeCurrent")
  public void captureSpanWithImplicitParentUsingTracingContextUtilsWithSpanAndMakeCurrent() {
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
                span0 -> span0.hasName("parent").hasNoParent(),
                span1 -> span1.hasName("test").hasParent(trace.getSpan(0))));
  }

  @Test
  @DisplayName("capture span with explicit parent")
  public void captureSpanWithExplicitParent() {
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
                span0 -> span0.hasName("parent").hasNoParent(),
                span1 -> span1.hasName("test").hasParent(trace.getSpan(0))));
  }

  @Test
  @DisplayName("capture span with explicit no parent")
  public void captureSpanWithExplicitNoParent() {
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
        trace0 -> trace0.hasSpansSatisfyingExactly(span -> span.hasName("parent").hasNoParent()),
        trace1 -> trace1.hasSpansSatisfyingExactly(span -> span.hasName("test").hasNoParent()));
  }

  @Test
  @DisplayName("capture name update")
  public void captureNameUpdate() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    testSpan.updateName("test2");
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test2").hasNoParent()));
  }

  @Test
  @DisplayName("capture exception()")
  public void captureException() {
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
                span ->
                    span.hasName("test")
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE,
                                            "java.lang.IllegalStateException"),
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                                            writer.toString())))));
  }

  @Test
  @DisplayName("capture exception with Attributes()")
  public void captureExceptionWithAttributes() {
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
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_TYPE,
                                            "java.lang.IllegalStateException"),
                                        equalTo(
                                            ExceptionAttributes.EXCEPTION_STACKTRACE,
                                            writer.toString()),
                                        equalTo(stringKey("dog"), "bark")))));
  }

  @Test
  @DisplayName("capture name update using TracingContextUtils.getCurrentSpan()")
  public void captureNameUpdateUsingTracingContextUtilsGetCurrentSpan() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    Scope testScope = Context.current().with(testSpan).makeCurrent();
    Span.current().updateName("test2");
    testScope.close();
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test2").hasNoParent()));
  }

  @Test
  @DisplayName("capture name update using TracingContextUtils.Span.fromContext(Context.current())")
  public void captureNameUpdateUsingTracingContextUtilsSpanFromContextCurrent() {
    // When
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").startSpan();
    Scope testScope = Context.current().with(testSpan).makeCurrent();
    Span.fromContext(Context.current()).updateName("test2");
    testScope.close();
    testSpan.end();

    // Then
    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("test2").hasNoParent()));
  }

  @Test
  @DisplayName("add wrapped span to context")
  public void addWrappedSpanToContext() {
    // When
    // Lazy way to get a span context
    Tracer tracer = GlobalOpenTelemetry.getTracer("test");
    Span testSpan = tracer.spanBuilder("test").setSpanKind(PRODUCER).startSpan();
    testSpan.end();

    Span span = Span.wrap(testSpan.getSpanContext());
    Context context = Context.current().with(span);

    // Then
    assertEquals(
        span.getSpanContext().getSpanId(), Span.fromContext(context).getSpanContext().getSpanId());
  }

  @Test
  @DisplayName("test tracer builder")
  public void testTracerBuilder() {
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
                        .hasInstrumentationScopeInfo(
                            InstrumentationScopeInfo.builder("test").setVersion("1.2.3").build())));
  }
}
