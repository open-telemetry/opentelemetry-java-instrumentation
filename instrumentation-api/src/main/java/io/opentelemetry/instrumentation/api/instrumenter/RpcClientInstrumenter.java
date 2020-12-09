/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import io.opentelemetry.api.trace.Tracer;

public abstract class RpcClientInstrumenter extends BaseInstrumenter {
  protected RpcClientInstrumenter() {}

  protected RpcClientInstrumenter(Tracer tracer) {
    super(tracer);
  }
}
