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
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
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
    String instrumentationVersion = EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
    TracerBuilder tracerBuilder =
        GlobalOpenTelemetry.get().getTracerProvider().tracerBuilder(INSTRUMENTATION_NAME);
    if (instrumentationVersion != null) {
      tracerBuilder.setInstrumentationVersion(instrumentationVersion);
    }
    tracer = tracerBuilder.build();
  }

  private static final Map<CircuitBreaker, Deque<Context>> parentContextsByCircuitBreaker =
      new WeakHashMap<>();

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

    synchronized (parentContextsByCircuitBreaker) {
      Deque<Context> parentContexts = parentContextsByCircuitBreaker.get(circuitBreaker);
      if (parentContexts == null) {
        parentContexts = new ArrayDeque<>();
        parentContextsByCircuitBreaker.put(circuitBreaker, parentContexts);
      }
      parentContexts.addLast(parentContext);
    }
  }

  public static void reject(CircuitBreaker circuitBreaker, @Nullable Throwable throwable) {
    Context parentContext = Context.current();
    SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
    if (!parentSpanContext.isValid()) {
      return;
    }

    Span span = startSpan(circuitBreaker, parentContext, 0);
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute(CIRCUIT_BREAKER_OUTCOME, "rejected");
    }
    if (throwable != null) {
      span.recordException(throwable);
    }
    span.setStatus(StatusCode.ERROR);
    span.end();
  }

  public static void end(
      CircuitBreaker circuitBreaker,
      String outcome,
      long durationNanos,
      @Nullable Throwable throwable) {
    Context parentContext;
    synchronized (parentContextsByCircuitBreaker) {
      Deque<Context> parentContexts = parentContextsByCircuitBreaker.get(circuitBreaker);
      if (parentContexts == null) {
        return;
      }
      parentContext = parentContexts.pollLast();
      if (parentContexts.isEmpty()) {
        parentContextsByCircuitBreaker.remove(circuitBreaker);
      }
    }
    if (parentContext == null) {
      return;
    }

    Span span = startSpan(circuitBreaker, parentContext, durationNanos);
    if (CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
      span.setAttribute(CIRCUIT_BREAKER_OUTCOME, outcome);
    }
    if (throwable != null) {
      span.recordException(throwable);
      span.setStatus(StatusCode.ERROR);
    }
    span.end();
  }

  private static Span startSpan(
      CircuitBreaker circuitBreaker, Context parentContext, long durationNanos) {
    long startTimeNanos = TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()) - durationNanos;
    Span span =
        tracer
            .spanBuilder("CircuitBreaker " + circuitBreaker.getName())
            .setParent(parentContext)
            .setSpanKind(SpanKind.INTERNAL)
            .setStartTimestamp(startTimeNanos, TimeUnit.NANOSECONDS)
            .startSpan();
    setStartAttributes(span, circuitBreaker);
    return span;
  }

  private static void setStartAttributes(Span span, CircuitBreaker circuitBreaker) {
    if (!CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES) {
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

  private Resilience4jCircuitBreakerSpans() {}
}
