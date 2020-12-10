/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

class NoopOperation<RESULT> implements Operation<RESULT> {
  private static final Operation<Object> INSTANCE = new NoopOperation();

  @SuppressWarnings("unchecked")
  static <RESULT> Operation<RESULT> noop() {
    return (Operation<RESULT>) INSTANCE;
  }

  @Override
  public Scope makeCurrent() {
    return Scope.noop();
  }

  @Override
  public Scope makeParentCurrent() {
    return Scope.noop();
  }

  @Override
  public void end() {}

  @Override
  public void end(RESULT result) {}

  @Override
  public void end(RESULT result, long endTimeNanos) {}

  @Override
  public void endExceptionally(Throwable throwable) {}

  @Override
  public void endExceptionally(Throwable throwable, RESULT result) {}

  @Override
  public void endExceptionally(Throwable throwable, RESULT result, long endTimeNanos) {}

  @Override
  public Span getSpan() {
    return Span.getInvalid();
  }

  @Override
  public <C> void inject(
      TextMapPropagator propagator, C carrier, TextMapPropagator.Setter<C> setter) {}
}
