/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class PropagatorBasedBaggageExtractor<REQUEST> implements ContextCustomizer<REQUEST> {

  private final TextMapPropagator propagator;
  private final TextMapGetter<REQUEST> getter;

  public PropagatorBasedBaggageExtractor(
      TextMapPropagator propagator, TextMapGetter<REQUEST> getter) {
    this.propagator = propagator;
    this.getter = getter;
  }

  @Override
  public Context onStart(Context parentContext, REQUEST request, Attributes startAttributes) {
    Context extracted = propagator.extract(parentContext, request, getter);
    Baggage baggage = Baggage.fromContext(extracted);
    return baggage.storeInContext(parentContext);
  }
}
