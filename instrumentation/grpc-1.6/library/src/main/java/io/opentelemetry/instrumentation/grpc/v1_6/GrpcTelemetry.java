/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.Internal;

/** Entrypoint for instrumenting gRPC servers or clients. */
public final class GrpcTelemetry {

  static {
    Internal.setServerInterceptorFactory(GrpcTelemetry::buildServerInterceptor);
  }

  /** Returns a new {@link GrpcTelemetry} configured with the given {@link OpenTelemetry}. */
  public static GrpcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a new {@link GrpcTelemetryBuilder} configured with the given {@link OpenTelemetry}. */
  public static GrpcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new GrpcTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<GrpcRequest, Status> serverInstrumenter;
  private final Instrumenter<GrpcRequest, Status> clientInstrumenter;
  private final ContextPropagators propagators;
  private final boolean captureExperimentalSpanAttributes;
  private final boolean emitMessageEvents;

  GrpcTelemetry(
      Instrumenter<GrpcRequest, Status> serverInstrumenter,
      Instrumenter<GrpcRequest, Status> clientInstrumenter,
      ContextPropagators propagators,
      boolean captureExperimentalSpanAttributes,
      boolean emitMessageEvents) {
    this.serverInstrumenter = serverInstrumenter;
    this.clientInstrumenter = clientInstrumenter;
    this.propagators = propagators;
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    this.emitMessageEvents = emitMessageEvents;
  }

  /**
   * Returns a new {@link ClientInterceptor} for use with methods like {@link
   * io.grpc.ManagedChannelBuilder#intercept(ClientInterceptor...)}.
   */
  public ClientInterceptor createClientInterceptor() {
    return new TracingClientInterceptor(
        clientInstrumenter, propagators, captureExperimentalSpanAttributes, emitMessageEvents);
  }

  /**
   * Configures a {@link ServerBuilder} with both the server interceptor and the stream tracer
   * factory. The interceptor handles registered service methods, while the stream tracer factory
   * creates spans for requests to unregistered services that are not seen by server interceptors.
   */
  public void configureServerBuilder(ServerBuilder<?> serverBuilder) {
    serverBuilder.intercept(buildServerInterceptor());
    serverBuilder.addStreamTracerFactory(
        new TracingServerStreamTracerFactory(serverInstrumenter, propagators));
  }

  /**
   * Returns a new {@link ServerInterceptor} for use with methods like {@link
   * io.grpc.ServerBuilder#intercept(ServerInterceptor)}.
   *
   * @deprecated Use {@link #configureServerBuilder(ServerBuilder)} instead, which also registers
   *     the stream tracer factory needed to capture requests to unregistered services.
   */
  @Deprecated
  public ServerInterceptor createServerInterceptor() {
    return buildServerInterceptor();
  }

  ServerInterceptor buildServerInterceptor() {
    return new TracingServerInterceptor(
        serverInstrumenter, captureExperimentalSpanAttributes, emitMessageEvents);
  }
}
