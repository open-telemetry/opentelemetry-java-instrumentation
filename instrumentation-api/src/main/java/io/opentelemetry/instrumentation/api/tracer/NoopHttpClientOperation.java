/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

public class NoopHttpClientOperation<RESPONSE> implements HttpClientOperation<RESPONSE> {
  private static final HttpClientOperation<Object> INSTANCE = new NoopHttpClientOperation<>();

  @SuppressWarnings("unchecked")
  static <RESPONSE> HttpClientOperation<RESPONSE> noop() {
    return (HttpClientOperation<RESPONSE>) INSTANCE;
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
  public void end(RESPONSE response) {}

  @Override
  public void end(RESPONSE response, long endTimeNanos) {}

  @Override
  public void endExceptionally(Throwable t) {}

  @Override
  public void endExceptionally(RESPONSE response, Throwable throwable) {}

  @Override
  public void endExceptionally(RESPONSE response, Throwable throwable, long endTimeNanos) {}

  @Override
  public Span getSpan() {
    return Span.getInvalid();
  }

  @Override
  public <REQUEST> void inject(
      REQUEST request, TextMapPropagator.Setter<REQUEST> setter, TextMapPropagator propagator) {}
}
