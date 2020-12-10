/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

class NoopHttpClientOperation implements HttpClientOperation {
  private static final HttpClientOperation INSTANCE = new NoopHttpClientOperation();

  static HttpClientOperation noop() {
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
