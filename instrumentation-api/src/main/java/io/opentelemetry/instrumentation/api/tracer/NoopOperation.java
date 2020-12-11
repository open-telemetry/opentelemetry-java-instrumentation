/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

class NoopOperation implements Operation {
  private static final Operation INSTANCE = new NoopOperation();

  static Operation noop() {
    return INSTANCE;
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
  public Span getSpan() {
    return Span.getInvalid();
  }

  @Override
  public <C> void inject(
      TextMapPropagator propagator, C carrier, TextMapPropagator.Setter<C> setter) {}
}
