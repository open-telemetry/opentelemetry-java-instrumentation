/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapGetter;

public abstract class RpcServerTracer<REQUEST> extends BaseTracer {

  protected RpcServerTracer() {}

  /**
   * Prefer to pass in an OpenTelemetry instance, rather than just a Tracer, so you don't have to
   * use the GlobalOpenTelemetry Propagator instance.
   *
   * @deprecated prefer to pass in an OpenTelemetry instance, instead.
   */
  @Deprecated
  protected RpcServerTracer(Tracer tracer) {
    super(tracer);
  }

  protected RpcServerTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  protected abstract TextMapGetter<REQUEST> getGetter();
}
