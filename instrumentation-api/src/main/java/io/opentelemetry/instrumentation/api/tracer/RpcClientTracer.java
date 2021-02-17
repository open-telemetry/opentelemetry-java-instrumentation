/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;

public abstract class RpcClientTracer extends BaseTracer {
  protected RpcClientTracer() {}

  /**
   * Prefer to pass in an OpenTelemetry instance, rather than just a Tracer, so you don't have to
   * use the GlobalOpenTelemetry Propagator instance.
   *
   * @deprecated prefer to pass in an OpenTelemetry instance, instead.
   */
  @Deprecated
  protected RpcClientTracer(Tracer tracer) {
    super(tracer);
  }

  protected RpcClientTracer(OpenTelemetry openTelemetry) {
    super(openTelemetry);
  }

  protected abstract String getRpcSystem();
}
