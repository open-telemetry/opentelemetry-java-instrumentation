/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.context.Context;

class DefaultLazyHttpClientOperation<REQUEST, CARRIER, RESPONSE>
    extends DefaultHttpClientOperation<REQUEST, CARRIER, RESPONSE>
    implements LazyHttpClientOperation<REQUEST, CARRIER, RESPONSE> {

  DefaultLazyHttpClientOperation(
      Context context, Context parentContext, HttpClientTracer<REQUEST, CARRIER, RESPONSE> tracer) {
    super(context, parentContext, tracer);
  }

  @Override
  public void onRequest(REQUEST request) {
    tracer.onRequest(getSpan()::setAttribute, request);
  }

  @Override
  public void inject(CARRIER carrier) {
    tracer.inject(carrier, context);
  }
}
