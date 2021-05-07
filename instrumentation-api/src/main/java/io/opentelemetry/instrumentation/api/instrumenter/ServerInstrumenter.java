/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.api.internal.ContextPropagationDebug;

final class ServerInstrumenter<REQUEST, RESPONSE> extends Instrumenter<REQUEST, RESPONSE> {

  private final ContextPropagators propagators;
  private final TextMapGetter<REQUEST> getter;

  ServerInstrumenter(
      InstrumenterBuilder<REQUEST, RESPONSE> builder, TextMapGetter<REQUEST> getter) {
    super(builder);
    this.propagators = builder.openTelemetry.getPropagators();
    this.getter = getter;
  }

  @Override
  public Context start(Context parentContext, REQUEST request) {
    ContextPropagationDebug.debugContextLeakIfEnabled();

    Context extracted = propagators.getTextMapPropagator().extract(parentContext, request, getter);
    return super.start(extracted, request);
  }
}
