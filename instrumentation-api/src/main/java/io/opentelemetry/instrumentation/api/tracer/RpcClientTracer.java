/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;

/**
 * Base class for implementing Tracers for RPC clients.
 *
 * @deprecated Use {@link io.opentelemetry.instrumentation.api.instrumenter.Instrumenter} and
 *     {@linkplain io.opentelemetry.instrumentation.api.instrumenter.rpc the RPC semantic convention
 *     utilities package} instead.
 */
@Deprecated
public abstract class RpcClientTracer extends BaseTracer {
  protected RpcClientTracer() {}

  protected RpcClientTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  protected abstract String getRpcSystem();
}
