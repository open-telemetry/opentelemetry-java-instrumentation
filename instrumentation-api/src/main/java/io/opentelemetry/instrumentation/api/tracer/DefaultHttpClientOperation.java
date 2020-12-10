/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

class DefaultHttpClientOperation<RESPONSE> implements HttpClientOperation<RESPONSE> {

  private final Context context;
  // TODO (trask) separate interface/implementation when parentContext is not needed as memory
  //  optimization?
  private final Context parentContext;
  private final HttpClientTracer<?, RESPONSE> tracer;

  DefaultHttpClientOperation(
      Context context, Context parentContext, HttpClientTracer<?, RESPONSE> tracer) {
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
  public void end() {
    end(null, -1);
  }

  @Override
  public void end(RESPONSE response) {
    end(response, -1);
  }

  @Override
  public void end(RESPONSE response, long endTimeNanos) {
    tracer.end(context, response, endTimeNanos);
  }

  @Override
  public void endExceptionally(Throwable throwable) {
    endExceptionally(throwable, null, -1);
  }

  @Override
  public void endExceptionally(Throwable throwable, RESPONSE response) {
    endExceptionally(throwable, response, -1);
  }

  @Override
  public void endExceptionally(Throwable throwable, RESPONSE response, long endTimeNanos) {
    tracer.endExceptionally(context, throwable, response, endTimeNanos);
  }

  @Override
  public Span getSpan() {
    return Span.fromContext(context);
  }

  @Override
  public <C> void inject(
      TextMapPropagator propagator, C carrier, TextMapPropagator.Setter<C> setter) {
    propagator.inject(context, carrier, setter);
  }
}
