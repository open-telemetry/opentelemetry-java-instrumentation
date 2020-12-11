/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

class DefaultOperation<RESULT> implements Operation<RESULT> {

  private final Context context;
  // TODO (trask) separate interface/implementation when parentContext is not needed as memory
  //  optimization?
  private final Context parentContext;
  private final HttpClientTracer<?, RESULT> tracer;

  DefaultOperation(Context context, Context parentContext, HttpClientTracer<?, RESULT> tracer) {
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
  public void end(RESULT result) {
    end(result, -1);
  }

  @Override
  public void end(RESULT result, long endTimeNanos) {
    tracer.end(context, result, endTimeNanos);
  }

  @Override
  public void endExceptionally(Throwable throwable) {
    endExceptionally(throwable, null, -1);
  }

  @Override
  public void endExceptionally(Throwable throwable, RESULT result) {
    endExceptionally(throwable, result, -1);
  }

  @Override
  public void endExceptionally(Throwable throwable, RESULT result, long endTimeNanos) {
    tracer.endExceptionally(context, throwable, result, endTimeNanos);
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
