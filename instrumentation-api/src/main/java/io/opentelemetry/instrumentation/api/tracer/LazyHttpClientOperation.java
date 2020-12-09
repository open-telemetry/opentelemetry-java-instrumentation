/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

// request is not available at the beginning of the operation
public interface LazyHttpClientOperation<REQUEST, RESPONSE> extends HttpClientOperation<RESPONSE> {

  static <REQUEST, RESPONSE> LazyHttpClientOperation<REQUEST, RESPONSE> noop() {
    return NoopLazyHttpClientOperation.noopUnbound();
  }

  static <REQUEST, RESPONSE> LazyHttpClientOperation<REQUEST, RESPONSE> create(
      Context context, Context parentContext, LazyHttpClientTracer<REQUEST, RESPONSE> tracer) {
    return new DefaultLazyHttpClientOperation<>(context, parentContext, tracer);
  }

  void onRequest(REQUEST request);

  // TODO (trask) instead of this, could make Context access public
  <CARRIER> void inject(
      TextMapPropagator propagator, CARRIER request, TextMapPropagator.Setter<CARRIER> setter);
}
