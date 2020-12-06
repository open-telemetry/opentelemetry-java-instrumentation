/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

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
  public Scope makeCurrent() {
    return context.makeCurrent();
  }

  @Override
  public Scope makeParentCurrent() {
    return parentContext.makeCurrent();
  }

  @Override
  public void end(RESPONSE response) {
    tracer.end(context, response);
  }

  @Override
  public void end(RESPONSE response, long endTimeNanos) {
    tracer.end(context, response, endTimeNanos);
  }

  @Override
  public void endExceptionally(Throwable throwable) {
    tracer.endExceptionally(context, throwable);
  }

  @Override
  public void endExceptionally(RESPONSE response, Throwable throwable) {
    tracer.endExceptionally(context, response, throwable);
  }

  @Override
  public void endExceptionally(RESPONSE response, Throwable throwable, long endTimeNanos) {}

  @Override
  public Span getSpan() {
    return Span.fromContext(context);
  }

  protected Context getContext() {
    return context;
  }
}
