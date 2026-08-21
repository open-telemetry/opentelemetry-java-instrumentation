/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.resilience4j.circuitbreaker.v0_15;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Locale;
import javax.annotation.Nullable;

public class Resilience4jCircuitBreakerSpans {

  private static final String INSTRUMENTATION_NAME =
      "io.opentelemetry.resilience4j-circuitbreaker-0.15";

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getInstrumentationConfig(
              GlobalOpenTelemetry.get(), "resilience4j_circuitbreaker")
          .getBoolean("experimental_span_attributes/development", false);

  private static final Tracer tracer;

  static {
    String instrumentationVersion =
        EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
    TracerBuilder tracerBuilder =
        GlobalOpenTelemetry.get().getTracerProvider().tracerBuilder(INSTRUMENTATION_NAME);
    if (instrumentationVersion != null) {
      tracerBuilder.setInstrumentationVersion(instrumentationVersion);
    }
    tracer = tracerBuilder.build();
  }

  private static final ThreadLocal<Deque<PendingSpan>> pendingSpans = new ThreadLocal<>();
  private static final ThreadLocal<Boolean> inCircuitBreakerCallback = new ThreadLocal<>();

  private static final AttributeKey<String> CIRCUIT_BREAKER_NAME =
      stringKey("resilience.policy.name");

  private static final AttributeKey<String> CIRCUIT_BREAKER_STATE =
      stringKey("resilience.circuit_breaker.state");

  private static final AttributeKey<String> CIRCUIT_BREAKER_OUTCOME =
      stringKey("resilience.circuit_breaker.outcome");

  public static void start(CircuitBreaker circuitBreaker) {
    Context parentContext = Context.current();
    SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
    if (!parentSpanContext.isValid()) {
      return;
    }

    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      spans = new ArrayDeque<>();
      pendingSpans.set(spans);
    }
    spans.push(new PendingSpan(circuitBreaker, startSpan(circuitBreaker, parentContext)));
  }

  public static void reject(CircuitBreaker circuitBreaker, @Nullable Throwable throwable) {
    Context parentContext = Context.current();
    SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
    if (!parentSpanContext.isValid()) {
      return;
    }

    Span span = startSpan(circuitBreaker, parentContext);
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute(CIRCUIT_BREAKER_OUTCOME, "rejected");
    }
    if (throwable != null) {
      span.recordException(throwable);
    }
    span.setStatus(StatusCode.ERROR);
    span.end();
  }

  public static void enterCircuitBreakerCallback() {
    inCircuitBreakerCallback.set(Boolean.TRUE);
  }

  public static void exitCircuitBreakerCallback() {
    inCircuitBreakerCallback.remove();
  }

  public static boolean isInCircuitBreakerCallback() {
    return Boolean.TRUE.equals(inCircuitBreakerCallback.get());
  }

  public static void end(
      CircuitBreaker circuitBreaker, String outcome, @Nullable Throwable throwable) {
    PendingSpan pendingSpan = pollPendingSpan(circuitBreaker);
    if (pendingSpan != null) {
      pendingSpan.end(outcome, throwable);
    }
  }

  public static void endAfter(
      @Nullable PendingSpan baseline, String outcome, @Nullable Throwable throwable) {
    PendingSpan pendingSpan = pollPendingSpanAfter(baseline);
    if (pendingSpan != null) {
      pendingSpan.end(outcome, throwable);
    }
  }

  @Nullable
  public static PendingSpan currentPendingSpan() {
    Deque<PendingSpan> spans = pendingSpans.get();
    return spans == null ? null : spans.peek();
  }

  @Nullable
  private static PendingSpan pollPendingSpan(CircuitBreaker circuitBreaker) {
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      return null;
    }
    Iterator<PendingSpan> iterator = spans.iterator();
    while (iterator.hasNext()) {
      PendingSpan span = iterator.next();
      if (span.circuitBreaker == circuitBreaker) {
        iterator.remove();
        if (spans.isEmpty()) {
          pendingSpans.remove();
        }
        return span;
      }
    }
    if (spans.isEmpty()) {
      pendingSpans.remove();
    }
    return null;
  }

  @Nullable
  public static PendingSpan pollPendingSpanAfter(@Nullable PendingSpan baseline) {
    Deque<PendingSpan> spans = pendingSpans.get();
    if (spans == null) {
      return null;
    }
    PendingSpan span = spans.peek();
    if (span == null || span == baseline) {
      if (spans.isEmpty()) {
        pendingSpans.remove();
      }
      return null;
    }
    span = spans.poll();
    if (spans.isEmpty()) {
      pendingSpans.remove();
    }
    return span;
  }

  private static Span startSpan(CircuitBreaker circuitBreaker, Context parentContext) {
    Span span =
        tracer
            .spanBuilder("CircuitBreaker " + circuitBreaker.getName())
            .setParent(parentContext)
            .setSpanKind(SpanKind.INTERNAL)
            .startSpan();
    setStartAttributes(span, circuitBreaker);
    return span;
  }

  private static void setStartAttributes(Span span, CircuitBreaker circuitBreaker) {
    if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES || !span.isRecording()) {
      return;
    }
    span.setAttribute(CIRCUIT_BREAKER_NAME, circuitBreaker.getName());
    span.setAttribute(
        CIRCUIT_BREAKER_STATE, circuitBreaker.getState().name().toLowerCase(Locale.ROOT));
  }

  @SuppressWarnings({"ReturnValueIgnored", "unused"})
  private static void limitSupportedVersions(CircuitBreaker circuitBreaker) {
    // Keep a reference to enforce 0.15.0 as the minimum version.
    circuitBreaker.tryAcquirePermission();
  }

  public static final class PendingSpan {
    private final CircuitBreaker circuitBreaker;
    private final Span span;
    private boolean ended;

    private PendingSpan(CircuitBreaker circuitBreaker, Span span) {
      this.circuitBreaker = circuitBreaker;
      this.span = span;
    }

    public synchronized void end(String outcome, @Nullable Throwable throwable) {
      if (ended) {
        return;
      }
      ended = true;
      if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
        span.setAttribute(CIRCUIT_BREAKER_OUTCOME, outcome);
      }
      if (throwable != null) {
        span.recordException(throwable);
        span.setStatus(StatusCode.ERROR);
      }
      span.end();
    }
  }

  private Resilience4jCircuitBreakerSpans() {}
}
