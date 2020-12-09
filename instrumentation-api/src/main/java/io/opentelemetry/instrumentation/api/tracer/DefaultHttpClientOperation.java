/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

public class DefaultHttpClientOperation<RESPONSE> implements HttpClientOperation<RESPONSE> {

  private final Context context;
  // TODO separate implementation when parentContext is not needed (memory optimization)?
  private final Context parentContext;
  // TODO (trask) move end/endExceptionally/etc to here and remove tracer reference
  private final HttpClientTracer<?, ?, RESPONSE> tracer;

  public DefaultHttpClientOperation(
      Context context, Context parentContext, HttpClientTracer<?, ?, RESPONSE> tracer) {
    this.context = context;
    this.parentContext = parentContext;
    this.tracer = tracer;
  }

  @Override
  public final Scope makeCurrent() {
    return context.makeCurrent();
  }

  @Override
  public final Scope makeParentCurrent() {
    return parentContext.makeCurrent();
  }

  @Override
  public final void end(RESPONSE response) {
    checkNotNull(response);
    end(response, -1);
  }

  @Override
  public final void end(RESPONSE response, long endTimeNanos) {
    checkNotNull(response);
    Span span = getSpan();
    tracer.onResponse(span, response);
    endSpan(span, endTimeNanos);
  }

  @Override
  public final void endExceptionally(Throwable throwable) {
    checkNotNull(throwable);
    endExceptionally(null, throwable);
  }

  @Override
  public final void endExceptionally(RESPONSE response, Throwable throwable) {
    checkNotNull(throwable);
    endExceptionally(response, throwable, -1);
  }

  @Override
  public final void endExceptionally(RESPONSE response, Throwable throwable, long endTimeNanos) {
    checkNotNull(throwable);
    Span span = getSpan();
    span.setStatus(StatusCode.ERROR);
    tracer.onResponse(span, response);
    tracer.onException(span, throwable);
    endSpan(span, endTimeNanos);
  }

  @Override
  public final Span getSpan() {
    return Span.fromContext(context);
  }

  // TODO (trask) should we just expose full context?
  protected final <REQUEST> void inject(
      REQUEST request, TextMapPropagator.Setter<REQUEST> setter, TextMapPropagator propagator) {
    propagator.inject(context, request, setter);
  }

  private static void endSpan(Span span, long endTimeNanos) {
    if (endTimeNanos > 0) {
      span.end(endTimeNanos, NANOSECONDS);
    } else {
      span.end();
    }
  }

  private static void checkNotNull(Object obj) {
    if (obj == null) {
      throw new NullPointerException();
    }
  }
}
