/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.TextMapGetter;

/**
 * Base class for implementing Tracers for RPC servers.
 *
 * @deprecated Use {@link io.opentelemetry.instrumentation.api.instrumenter.Instrumenter} and
 *     {@linkplain io.opentelemetry.instrumentation.api.instrumenter.rpc the RPC semantic convention
 *     utilities package} instead.
 */
@Deprecated
public abstract class RpcServerTracer<REQUEST> extends BaseTracer {

  protected RpcServerTracer() {}

  protected RpcServerTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  protected abstract TextMapGetter<REQUEST> getGetter();
}
