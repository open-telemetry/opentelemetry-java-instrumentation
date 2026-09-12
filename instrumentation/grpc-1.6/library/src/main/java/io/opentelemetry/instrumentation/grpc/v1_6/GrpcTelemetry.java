/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;

import io.grpc.ClientInterceptor;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

/** Entrypoint for instrumenting gRPC servers or clients. */
public final class GrpcTelemetry {
  private final Instrumenter<GrpcRequest, Status> serverInstrumenter;
  private final Instrumenter<GrpcRequest, Status> clientInstrumenter;
  private final ContextPropagators propagators;
  private final boolean captureExperimentalSpanAttributes;
  private final boolean emitMessageEvents;

  /** Returns a new {@link GrpcTelemetry} configured with the given {@link OpenTelemetry}. */
  public static GrpcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a new {@link GrpcTelemetryBuilder} configured with the given {@link OpenTelemetry}. */
  public static GrpcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new GrpcTelemetryBuilder(openTelemetry);
  }

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
   * Configures a {@link ServerBuilder} with the server interceptor, which handles registered
   * service methods.
   *
   * <p>When stable RPC semantic conventions are enabled, through {@code
   * otel.semconv-stability.opt-in=rpc} or {@code otel.semconv-stability.opt-in=rpc/dup}, this also
   * registers a stream tracer factory that creates spans for requests to unregistered services,
   * which server interceptors never see. The factory is left off otherwise, because those spans are
   * only produced under the stable conventions.
   */
  public void configureServerBuilder(ServerBuilder<?> serverBuilder) {
    serverBuilder.intercept(createServerInterceptor());
    if (emitStableRpcSemconv()) {
      serverBuilder.addStreamTracerFactory(
          new TracingServerStreamTracerFactory(serverInstrumenter, propagators));
    }
  }

  /**
   * Returns a new {@link ServerInterceptor} for use with methods like {@link
   * io.grpc.ServerBuilder#intercept(ServerInterceptor)} and {@link
   * io.grpc.ServerInterceptors#intercept(io.grpc.ServerServiceDefinition, ServerInterceptor...)}.
   *
   * <p>An interceptor on its own does not see requests to services that are not registered on the
   * server. Prefer {@link #configureServerBuilder(ServerBuilder)} where a {@link ServerBuilder} is
   * available, because it can also register the stream tracer factory that captures those requests.
   */
  public ServerInterceptor createServerInterceptor() {
    return new TracingServerInterceptor(
        serverInstrumenter, captureExperimentalSpanAttributes, emitMessageEvents);
  }
}
