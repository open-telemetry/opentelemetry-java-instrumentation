/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.grpc.ServerStreamTracer;
import io.grpc.Status;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

final class TracingServerStreamTracerFactory extends ServerStreamTracer.Factory {

  private final Instrumenter<GrpcRequest, Status> instrumenter;
  private final ContextPropagators propagators;

  TracingServerStreamTracerFactory(
      Instrumenter<GrpcRequest, Status> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  @Override
  public ServerStreamTracer newServerStreamTracer(String fullMethodName, Metadata headers) {
    return new TracingServerStreamTracer(
        instrumenter, propagators, fullMethodName, headers, Context.current());
  }
}
