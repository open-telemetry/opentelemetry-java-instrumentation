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

class DefaultHttpClientOperation<REQUEST, CARRIER, RESPONSE>
    implements HttpClientOperation<RESPONSE> {

  protected final Context context;
  // TODO separate implementation when parentContext is not needed (memory optimization)?
  protected final Context parentContext;
  protected final HttpClientTracer<REQUEST, CARRIER, RESPONSE> tracer;

  DefaultHttpClientOperation(
      Context context, Context parentContext, HttpClientTracer<REQUEST, CARRIER, RESPONSE> tracer) {
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
    endExceptionally(null, throwable, -1);
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
    if (response != null) {
      tracer.onResponse(span, response);
    }
    tracer.onException(span, throwable);
    span.setStatus(StatusCode.ERROR);
    endSpan(span, endTimeNanos);
  }

  @Override
  public final Span getSpan() {
    return Span.fromContext(context);
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
