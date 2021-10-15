/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.InstrumentationVersion;
import io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

// TODO(anuraaga): Need to define what are actually useful knobs, perhaps even providing a
// base-class
// for instrumentation library builders.
/**
 * An instrumenter of the start and end of a request/response lifecycle. Almost all instrumentation
 * of libraries falls into modeling start and end, generating observability signals from these such
 * as a tracing {@link Span}, or metrics such as the duration taken, active requests, etc. When
 * instrumenting a library, there will generally be four steps.
 *
 * <ul>
 *   <li>Create an {@link Instrumenter} using {@link InstrumenterBuilder}. Use the builder to
 *       configure any library-specific customizations, and also expose useful knobs to your user.
 *   <li>Call {@link Instrumenter#shouldStart(Context, Object)} and do not proceed if {@code false}.
 *   <li>Call {@link Instrumenter#start(Context, Object)} at the beginning of a request.
 *   <li>Call {@link Instrumenter#end(Context, Object, Object, Throwable)} at the end of a request.
 * </ul>
 */
public class Instrumenter<REQUEST, RESPONSE> {

  /**
   * Returns a new {@link InstrumenterBuilder}.
   *
   * <p>The {@code instrumentationName} is the name of the instrumentation library, not the name of
   * the instrument*ed* library. The value passed in this parameter should uniquely identify the
   * instrumentation library so that during troubleshooting it's possible to pinpoint what tracer
   * produced problematic telemetry.
   *
   * <p>In this project we use a convention to encode the minimum supported version of the
   * instrument*ed* library into the instrumentation name, for example {@code
   * io.opentelemetry.apache-httpclient-4.0}. This way, if there are different instrumentations for
   * different library versions it's easy to find out which instrumentations produced the telemetry
   * data.
   */
  public static <REQUEST, RESPONSE> InstrumenterBuilder<REQUEST, RESPONSE> newBuilder(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      SpanNameExtractor<? super REQUEST> spanNameExtractor) {
    return new InstrumenterBuilder<>(openTelemetry, instrumentationName, spanNameExtractor);
  }

  private static final SupportabilityMetrics supportability = SupportabilityMetrics.instance();

  private final String instrumentationName;
  private final Tracer tracer;
  private final SpanNameExtractor<? super REQUEST> spanNameExtractor;
  private final SpanKindExtractor<? super REQUEST> spanKindExtractor;
  private final SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor;
  private final List<? extends SpanLinksExtractor<? super REQUEST>> spanLinksExtractors;
  private final List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>>
      attributesExtractors;
  private final List<? extends ContextCustomizer<? super REQUEST>> contextCustomizers;
  private final List<? extends RequestListener> requestListeners;
  private final ErrorCauseExtractor errorCauseExtractor;
  @Nullable private final StartTimeExtractor<REQUEST> startTimeExtractor;
  @Nullable private final EndTimeExtractor<REQUEST, RESPONSE> endTimeExtractor;
  private final boolean disabled;
  private final SpanSuppressionStrategy spanSuppressionStrategy;

  Instrumenter(InstrumenterBuilder<REQUEST, RESPONSE> builder) {
    this.instrumentationName = builder.instrumentationName;
    this.tracer =
        builder.openTelemetry.getTracer(instrumentationName, InstrumentationVersion.VERSION);
    this.spanNameExtractor = builder.spanNameExtractor;
    this.spanKindExtractor = builder.spanKindExtractor;
    this.spanStatusExtractor = builder.spanStatusExtractor;
    this.spanLinksExtractors = new ArrayList<>(builder.spanLinksExtractors);
    this.attributesExtractors = new ArrayList<>(builder.attributesExtractors);
    this.contextCustomizers = new ArrayList<>(builder.contextCustomizers);
    this.requestListeners = new ArrayList<>(builder.requestListeners);
    this.errorCauseExtractor = builder.errorCauseExtractor;
    this.startTimeExtractor = builder.startTimeExtractor;
    this.endTimeExtractor = builder.endTimeExtractor;
    this.disabled = builder.disabled;
    this.spanSuppressionStrategy = builder.getSpanSuppressionStrategy();
  }

  /**
   * Returns whether instrumentation should be applied for the {@link REQUEST}. If {@code true},
   * call {@link #start(Context, Object)} and {@link #end(Context, Object, Object, Throwable)}
   * around the operation being instrumented, or if {@code false} execute the operation directly
   * without calling those methods.
   */
  public boolean shouldStart(Context parentContext, REQUEST request) {
    if (disabled) {
      return false;
    }
    SpanKind spanKind = spanKindExtractor.extract(request);
    boolean suppressed = spanSuppressionStrategy.shouldSuppress(parentContext, spanKind);

    if (suppressed) {
      supportability.recordSuppressedSpan(spanKind, instrumentationName);
    }
    return !suppressed;
  }

  /**
   * Starts a new operation to be instrumented. The {@code parentContext} is the parent of the
   * resulting instrumented operation and should usually be {@code Context.current()}. The {@code
   * request} is the request object of this operation. The returned {@link Context} should be
   * propagated along with the operation and passed to {@link #end(Context, Object, Object,
   * Throwable)} when it is finished.
   */
  public Context start(Context parentContext, REQUEST request) {
    SpanKind spanKind = spanKindExtractor.extract(request);
    SpanBuilder spanBuilder =
        tracer
            .spanBuilder(spanNameExtractor.extract(request))
            .setSpanKind(spanKind)
            .setParent(parentContext);

    Instant startTime = null;
    if (startTimeExtractor != null) {
      startTime = startTimeExtractor.extract(request);
      spanBuilder.setStartTimestamp(startTime);
    }

    SpanLinksBuilder spanLinksBuilder = new SpanLinksBuilderImpl(spanBuilder);
    for (SpanLinksExtractor<? super REQUEST> spanLinksExtractor : spanLinksExtractors) {
      spanLinksExtractor.extract(spanLinksBuilder, parentContext, request);
    }

    UnsafeAttributes attributesBuilder = new UnsafeAttributes();
    for (AttributesExtractor<? super REQUEST, ? super RESPONSE> extractor : attributesExtractors) {
      extractor.onStart(attributesBuilder, request);
    }
    Attributes attributes = attributesBuilder;

    Context context = parentContext;

    for (ContextCustomizer<? super REQUEST> contextCustomizer : contextCustomizers) {
      context = contextCustomizer.start(context, request, attributes);
    }

    if (!requestListeners.isEmpty()) {
      long startNanos = getNanos(startTime);
      for (RequestListener requestListener : requestListeners) {
        context = requestListener.start(context, attributes, startNanos);
      }
    }

    spanBuilder.setAllAttributes(attributes);
    Span span = spanBuilder.startSpan();
    context = context.with(span);

    return spanSuppressionStrategy.storeInContext(context, spanKind, span);
  }

  /**
   * Ends an instrumented operation. The {@link Context} must be what was returned from {@link
   * #start(Context, Object)}. {@code request} is the request object of the operation, {@code
   * response} is the response object of the operation, and {@code error} is an exception that was
   * thrown by the operation, or {@code null} if none was thrown.
   */
  public void end(
      Context context, REQUEST request, @Nullable RESPONSE response, @Nullable Throwable error) {
    Span span = Span.fromContext(context);

    if (error != null) {
      error = errorCauseExtractor.extractCause(error);
      span.recordException(error);
    }

    UnsafeAttributes attributes = new UnsafeAttributes();
    for (AttributesExtractor<? super REQUEST, ? super RESPONSE> extractor : attributesExtractors) {
      extractor.onEnd(attributes, request, response, error);
    }
    span.setAllAttributes(attributes);

    Instant endTime = null;
    if (endTimeExtractor != null) {
      endTime = endTimeExtractor.extract(request, response, error);
    }

    if (!requestListeners.isEmpty()) {
      long endNanos = getNanos(endTime);
      for (RequestListener requestListener : requestListeners) {
        requestListener.end(context, attributes, endNanos);
      }
    }

    SpanKind kind = spanKindExtractor.extract(request);
    StatusCode statusCode = spanStatusExtractor.extract(request, response, kind, error);
    if (statusCode != StatusCode.UNSET) {
      span.setStatus(statusCode);
    }

    if (endTime != null) {
      span.end(endTime);
    } else {
      span.end();
    }
  }

  private static long getNanos(@Nullable Instant time) {
    if (time == null) {
      return System.nanoTime();
    }
    return TimeUnit.SECONDS.toNanos(time.getEpochSecond()) + time.getNano();
  }
}
