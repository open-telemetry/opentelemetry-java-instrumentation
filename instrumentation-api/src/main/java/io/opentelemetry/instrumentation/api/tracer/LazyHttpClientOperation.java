/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.context.Context;

// request is not available at the beginning of the operation
public interface LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE>
    extends HttpClientOperation<RESPONSE> {

  static <REQUEST, CARRIER, RESPONSE> LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE> noop() {
    return NoopLazyHttpClientOperation.noopUnbound();
  }

  static <REQUEST, CARRIER, RESPONSE> LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE> create(
      Context context, Context parentContext, HttpClientTracer<REQUEST, CARRIER, RESPONSE> tracer) {
    return new DefaultLazyHttpClientOperation<>(context, parentContext, tracer);
  }

  void onRequest(REQUEST request);

  void inject(CARRIER carrier);
}
