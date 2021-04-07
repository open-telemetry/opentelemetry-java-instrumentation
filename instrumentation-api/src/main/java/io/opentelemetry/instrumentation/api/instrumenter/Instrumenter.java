/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SupportabilityMetrics;
import io.opentelemetry.instrumentation.api.tracer.ClientSpan;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

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

  /** Returns a new {@link InstrumenterBuilder}. */
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
  private final List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> extractors;
  private final ErrorCauseExtractor errorCauseExtractor;

  Instrumenter(
      String instrumentationName,
      Tracer tracer,
      SpanNameExtractor<? super REQUEST> spanNameExtractor,
      SpanKindExtractor<? super REQUEST> spanKindExtractor,
      SpanStatusExtractor<? super REQUEST, ? super RESPONSE> spanStatusExtractor,
      List<? extends AttributesExtractor<? super REQUEST, ? super RESPONSE>> extractors,
      ErrorCauseExtractor errorCauseExtractor) {
    this.instrumentationName = instrumentationName;
    this.tracer = tracer;
    this.spanNameExtractor = spanNameExtractor;
    this.spanKindExtractor = spanKindExtractor;
    this.spanStatusExtractor = spanStatusExtractor;
    this.extractors = extractors;
    this.errorCauseExtractor = errorCauseExtractor;
  }

  /**
   * Returns whether instrumentation should be applied for the {@link REQUEST}. If {@code true},
   * call {@link #start(Context, Object)} and {@link #end(Context, Object, Object, Throwable)}
   * around the operation being instrumented, or if {@code false} execute the operation directly
   * without calling those methods.
   */
  public boolean shouldStart(Context parentContext, REQUEST request) {
    boolean suppressed = false;
    SpanKind spanKind = spanKindExtractor.extract(request);
    switch (spanKind) {
      case SERVER:
        suppressed = ServerSpan.fromContextOrNull(parentContext) != null;
        break;
      case CLIENT:
        suppressed = ClientSpan.fromContextOrNull(parentContext) != null;
        break;
      default:
        break;
    }
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

    AttributesBuilder attributes = Attributes.builder();
    for (AttributesExtractor<? super REQUEST, ? super RESPONSE> extractor : extractors) {
      extractor.onStart(attributes, request);
    }
    attributes.build().forEach((key, value) -> spanBuilder.setAttribute((AttributeKey) key, value));

    Span span = spanBuilder.startSpan();
    Context context = parentContext.with(span);
    switch (spanKind) {
      case SERVER:
        return ServerSpan.with(context, span);
      case CLIENT:
        return ClientSpan.with(context, span);
      default:
        return context;
    }
  }

  /**
   * Ends an instrumented operation. The {@link Context} must be what was returned from {@link
   * #start(Context, Object)}. {@code request} is the request object of the operation, {@code
   * response} is the response object of the operation, and {@code error} is an exception that was
   * thrown by the operation, or {@code null} if none was thrown.
   */
  public void end(Context context, REQUEST request, RESPONSE response, @Nullable Throwable error) {
    Span span = Span.fromContext(context);

    AttributesBuilder attributes = Attributes.builder();
    for (AttributesExtractor<? super REQUEST, ? super RESPONSE> extractor : extractors) {
      extractor.onEnd(attributes, request, response);
    }
    attributes.build().forEach((key, value) -> span.setAttribute((AttributeKey) key, value));

    if (error != null) {
      error = errorCauseExtractor.extractCause(error);
      span.recordException(error);
    }

    span.setStatus(spanStatusExtractor.extract(request, response, error));

    span.end();
  }
}
