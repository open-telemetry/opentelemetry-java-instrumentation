/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.incubator.trace.ExtendedSpanBuilder;
import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class TracerTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void isEnabled() {
    Tracer disabledTracer = testing.getOpenTelemetry().getTracer("disabled-tracer");
    Tracer enabledTracer = testing.getOpenTelemetry().getTracer("enabled-tracer");
    testEnabled(disabledTracer, false);
    testEnabled(enabledTracer, true);
  }

  private static void testEnabled(Tracer tracer, boolean expected) {
    assertThat(tracer).isInstanceOf(ExtendedTracer.class);
    assertThat(((ExtendedTracer) tracer).isEnabled()).isEqualTo(expected);
  }

  @Test
  void extendedSpanBuilder() {
    Tracer tracer = testing.getOpenTelemetry().getTracer("test");
    SpanBuilder spanBuilder = tracer.spanBuilder("test");
    assertThat(spanBuilder).isInstanceOf(ExtendedSpanBuilder.class);

    ExtendedSpanBuilder builder = (ExtendedSpanBuilder) spanBuilder;
    {
      Span span = builder.startAndCall(Span::current);
      assertThat(span.getSpanContext().getTraceId()).isNotEqualTo(TraceId.getInvalid());
    }
    {
      Span span = builder.startAndCall(Span::current, (s, t) -> {});
      assertThat(span.getSpanContext().getTraceId()).isNotEqualTo(TraceId.getInvalid());
    }
    {
      AtomicReference<Span> spanRef = new AtomicReference<>();
      assertThatThrownBy(
              () ->
                  builder.startAndCall(
                      () -> {
                        throw new IllegalStateException("fail");
                      },
                      (s, t) -> spanRef.set(s)))
          .isInstanceOf(IllegalStateException.class);
      assertThat(spanRef.get().getSpanContext().getTraceId()).isNotEqualTo(TraceId.getInvalid());
    }

    {
      AtomicReference<Span> spanRef = new AtomicReference<>();
      builder.startAndRun(() -> spanRef.set(Span.current()));
      assertThat(spanRef.get().getSpanContext().getTraceId()).isNotEqualTo(TraceId.getInvalid());
    }
    {
      AtomicReference<Span> spanRef = new AtomicReference<>();
      builder.startAndRun(() -> spanRef.set(Span.current()), (s, t) -> {});
      assertThat(spanRef.get().getSpanContext().getTraceId()).isNotEqualTo(TraceId.getInvalid());
    }
    {
      AtomicReference<Span> spanRef = new AtomicReference<>();
      assertThatThrownBy(
              () ->
                  builder.startAndRun(
                      () -> {
                        throw new IllegalStateException("fail");
                      },
                      (s, t) -> spanRef.set(s)))
          .isInstanceOf(IllegalStateException.class);
      assertThat(spanRef.get().getSpanContext().getTraceId()).isNotEqualTo(TraceId.getInvalid());
    }

    Map<String, String> map = new HashMap<>();
    Context context =
        Context.root()
            .with(
                Span.wrap(
                    SpanContext.create(
                        TraceId.fromLongs(0, 1),
                        SpanId.fromLong(2),
                        TraceFlags.getDefault(),
                        TraceState.getDefault())));

    W3CTraceContextPropagator.getInstance()
        .inject(context, map, (m, key, value) -> m.put(key, value));
    builder.setParentFrom(ContextPropagators.create(W3CTraceContextPropagator.getInstance()), map);
    Span span = builder.startSpan();
    assertThat(span.getSpanContext().getTraceId()).isEqualTo(TraceId.fromLongs(0, 1));
  }
}
