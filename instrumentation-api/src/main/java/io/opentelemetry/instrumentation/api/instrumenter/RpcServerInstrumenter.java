/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;

public abstract class RpcServerInstrumenter<REQUEST> extends BaseInstrumenter {

  protected RpcServerInstrumenter() {}

  protected RpcServerInstrumenter(Tracer tracer) {
    super(tracer);
  }

  protected abstract TextMapPropagator.Getter<REQUEST> getGetter();
}
