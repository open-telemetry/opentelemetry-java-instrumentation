/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapPropagator;

class DefaultOperation implements Operation {

  private final Context context;
  // TODO (trask) separate interface/implementation when parentContext is not needed as memory
  //  optimization?
  private final Context parentContext;

  DefaultOperation(Context context, Context parentContext) {
    this.context = context;
    this.parentContext = parentContext;
  }

  @Override
  public final Scope makeCurrent() {
    return context.makeCurrent();
  }

  @Override
  public final Scope makeParentCurrent() {
    return parentContext.makeCurrent();
  }

  @Override
  public final Span getSpan() {
    return Span.fromContext(context);
  }

  @Override
  public <C> void inject(
      TextMapPropagator propagator, C carrier, TextMapPropagator.Setter<C> setter) {
    propagator.inject(context, carrier, setter);
  }
}
