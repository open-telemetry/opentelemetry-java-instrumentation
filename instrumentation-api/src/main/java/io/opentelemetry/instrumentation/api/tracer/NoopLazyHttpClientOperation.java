/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.context.propagation.TextMapPropagator;

class NoopLazyHttpClientOperation<REQUEST, RESPONSE> extends NoopHttpClientOperation<RESPONSE>
    implements LazyHttpClientOperation<REQUEST, RESPONSE> {

  private static final LazyHttpClientOperation<Object, Object> INSTANCE =
      new NoopLazyHttpClientOperation<>();

  @SuppressWarnings("unchecked")
  static <REQUEST, RESPONSE> LazyHttpClientOperation<REQUEST, RESPONSE> noopUnbound() {
    return (LazyHttpClientOperation<REQUEST, RESPONSE>) INSTANCE;
  }

  @Override
  public void onRequest(REQUEST request) {}

  @Override
  public <CARRIER> void inject(
      TextMapPropagator propagator, CARRIER request, TextMapPropagator.Setter<CARRIER> setter) {}
}
