/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;

class DefaultLazyHttpClientOperation<REQUEST, RESPONSE>
    extends DefaultHttpClientOperation<REQUEST, RESPONSE, LazyHttpClientTracer<REQUEST, RESPONSE>>
    implements LazyHttpClientOperation<REQUEST, RESPONSE> {

  DefaultLazyHttpClientOperation(
      Context context, Context parentContext, LazyHttpClientTracer<REQUEST, RESPONSE> tracer) {
    super(context, parentContext, tracer);
  }

  @Override
  public void onRequest(REQUEST request) {
    tracer.onRequest(getSpan(), request);
  }

  @Override
  public <CARRIER> void inject(
      TextMapPropagator propagator, CARRIER carrier, TextMapPropagator.Setter<CARRIER> setter) {
    propagator.inject(context, carrier, setter);
  }
}
