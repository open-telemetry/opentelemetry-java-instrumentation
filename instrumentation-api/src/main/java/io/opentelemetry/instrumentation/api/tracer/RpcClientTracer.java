/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer;

import io.opentelemetry.api.trace.Tracer;

public abstract class RpcClientTracer extends BaseTracer {
  protected RpcClientTracer() {}

  protected RpcClientTracer(Tracer tracer) {
    super(tracer);
  }
}
