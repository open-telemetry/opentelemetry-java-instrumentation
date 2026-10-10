/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SpanBridgeTest {

  private static final String TRACE_ID = "0123456789abcdef0123456789abcdef";
  private static final String SPAN_ID = "0123456789abcdef";

  @ParameterizedTest
  @MethodSource("spanContexts")
  void cachesSpanContext(SpanContext agentContext) {
    Span agentSpan = mock(Span.class);
    when(agentSpan.getSpanContext()).thenReturn(agentContext);
    ApplicationSpan span = new ApplicationSpan(agentSpan);

    application.io.opentelemetry.api.trace.SpanContext context = span.getSpanContext();

    assertThat(span.getSpanContext()).isSameAs(context);
    assertThat(context.getTraceId()).isEqualTo(agentContext.getTraceId());
    assertThat(context.getSpanId()).isEqualTo(agentContext.getSpanId());
    assertThat(context.getTraceFlags().asByte()).isEqualTo(agentContext.getTraceFlags().asByte());
    assertThat(context.getTraceState().get("vendor")).isEqualTo("value");
    assertThat(context.isRemote()).isEqualTo(agentContext.isRemote());
    assertThat(context.isValid()).isTrue();
    verify(agentSpan, times(1)).getSpanContext();
  }

  private static Stream<Arguments> spanContexts() {
    TraceState traceState = TraceState.builder().put("vendor", "value").build();
    return Stream.of(
        argumentSet(
            "local sampled",
            SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getSampled(), traceState)),
        argumentSet(
            "local unsampled",
            SpanContext.create(TRACE_ID, SPAN_ID, TraceFlags.getDefault(), traceState)),
        argumentSet(
            "remote sampled",
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, TraceFlags.getSampled(), traceState)),
        argumentSet(
            "remote unsampled",
            SpanContext.createFromRemoteParent(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), traceState)));
  }

  @Test
  void unwrapsWithoutConvertingSpanContext() {
    Span agentSpan =
        Span.wrap(
            SpanContext.create(
                TRACE_ID, SPAN_ID, TraceFlags.getDefault(), TraceState.getDefault()));
    ApplicationSpan span =
        new ApplicationSpan(agentSpan) {
          @Override
          public application.io.opentelemetry.api.trace.SpanContext getSpanContext() {
            throw new AssertionError("Unwrapping must not convert the application span context");
          }
        };

    assertThat(Bridging.toAgentOrNull(span)).isSameAs(agentSpan);
  }

  @Test
  void unwrapsInvalidAgentSpan() {
    Span agentSpan = mock(Span.class);
    when(agentSpan.getSpanContext()).thenReturn(SpanContext.getInvalid());

    assertThat(Bridging.toAgentOrNull(new ApplicationSpan(agentSpan))).isSameAs(Span.getInvalid());
  }

  @Test
  void invalidApplicationSpanReturnsInvalidAgentSpan() {
    assertThat(Bridging.toAgentOrNull(application.io.opentelemetry.api.trace.Span.getInvalid()))
        .isSameAs(Span.getInvalid());
  }

  @Test
  void validNonBridgedSpanReturnsNull() {
    application.io.opentelemetry.api.trace.Span span =
        application.io.opentelemetry.api.trace.Span.wrap(
            application.io.opentelemetry.api.trace.SpanContext.create(
                TRACE_ID,
                SPAN_ID,
                application.io.opentelemetry.api.trace.TraceFlags.getDefault(),
                application.io.opentelemetry.api.trace.TraceState.getDefault()));

    assertThat(Bridging.toAgentOrNull(span)).isNull();
  }
}
