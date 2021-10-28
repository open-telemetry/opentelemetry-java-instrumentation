/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for tracing gRPC servers or clients. */
public final class GrpcTracing {

  /** Returns a new {@link GrpcTracing} configured with the given {@link OpenTelemetry}. */
  public static GrpcTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a new {@link GrpcTracingBuilder} configured with the given {@link OpenTelemetry}. */
  public static GrpcTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new GrpcTracingBuilder(openTelemetry);
  }

  private final Instrumenter<GrpcRequest, Status> serverInstrumenter;
  private final Instrumenter<GrpcRequest, Status> clientInstrumenter;
  private final ContextPropagators propagators;
  private final boolean captureExperimentalSpanAttributes;

  GrpcTracing(
      Instrumenter<GrpcRequest, Status> serverInstrumenter,
      Instrumenter<GrpcRequest, Status> clientInstrumenter,
      ContextPropagators propagators,
      boolean captureExperimentalSpanAttributes) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
    this.propagators = propagators;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  /**
   * Returns a new {@link ClientInterceptor} for use with methods like {@link
   * io.grpc.ManagedChannelBuilder#intercept(ClientInterceptor...)}.
   */
  public ClientInterceptor newClientInterceptor() {
    return new TracingClientInterceptor(clientInstrumenter, propagators);
  }

  /**
   * Returns a new {@link ServerInterceptor} for use with methods like {@link
   * io.grpc.ServerBuilder#intercept(ServerInterceptor)}.
   */
  public ServerInterceptor newServerInterceptor() {
    return new TracingServerInterceptor(serverInstrumenter, captureExperimentalSpanAttributes);
  }
}
