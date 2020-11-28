/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;

public abstract class RpcServerTracer<REQUEST> extends BaseTracer {

  protected RpcServerTracer() {}

  protected RpcServerTracer(Tracer tracer) {
    super(tracer);
  }

  protected abstract TextMapPropagator.Getter<REQUEST> getGetter();
}
