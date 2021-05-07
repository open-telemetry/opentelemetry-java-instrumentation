/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapSetter;

final class ClientInstrumenter<REQUEST, RESPONSE> extends Instrumenter<REQUEST, RESPONSE> {

  private final ContextPropagators propagators;
  private final TextMapSetter<REQUEST> setter;

  ClientInstrumenter(
      InstrumenterBuilder<REQUEST, RESPONSE> builder, TextMapSetter<REQUEST> setter) {
    super(builder);
    this.propagators = builder.openTelemetry.getPropagators();
    this.setter = setter;
  }

  @Override
  public Context start(Context parentContext, REQUEST request) {
    Context newContext = super.start(parentContext, request);
    propagators.getTextMapPropagator().inject(newContext, request, setter);
    return newContext;
  }
}
