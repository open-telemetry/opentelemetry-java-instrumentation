/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

class NoopLazyHttpClientOperation<REQUEST, CARRIER, RESPONSE>
    extends NoopHttpClientOperation<RESPONSE>
    implements LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE> {

  private static final LazyHttpClientOperation<Object, Object, Object> INSTANCE =
      new NoopLazyHttpClientOperation<>();

  @SuppressWarnings("unchecked")
  static <REQUEST, CARRIER, RESPONSE>
      LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE> noopUnbound() {
    return (LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE>) INSTANCE;
  }

  @Override
  public void onRequest(REQUEST request) {}

  @Override
  public void inject(CARRIER request) {}
}
