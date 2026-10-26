/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import static java.util.Objects.requireNonNull;

import io.grpc.ClientInterceptor;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.Internal;
import javax.annotation.Nullable;

/** Entrypoint for instrumenting gRPC servers or clients. */
public final class GrpcTelemetry {

  static {
    Internal.setClientInterceptorFactory(
        (telemetry, target) -> telemetry.newTracingClientInterceptor(target, true));
  }

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
   *
   * <p>Uses the channel's authority for {@code server.address} and {@code server.port}. Use {@link
   * #createClientInterceptor(String)} when the configured channel target is available.
   */
  public ClientInterceptor createClientInterceptor() {
    return newTracingClientInterceptor(null, false);
  }

  /**
   * Returns a new {@link ClientInterceptor} that uses the configured channel target for {@code
   * server.address} and {@code server.port}.
   *
   * <p>Pass the same target used to configure the channel, not an authority override. For custom
   * name resolvers, use an explicit {@code scheme://...} target to distinguish it from a {@code
   * host:port} address. If the target does not identify a usable endpoint, the interceptor falls
   * back to the channel's authority.
   *
   * <p>This method does not depend on gRPC's internal target-aware interception API.
   *
   * @param target the configured gRPC channel target, for example {@code dns:///host:8443}
   */
  public ClientInterceptor createClientInterceptor(String target) {
    return newTracingClientInterceptor(requireNonNull(target, "target"), false);
  }

  /**
   * Returns a new {@link ServerInterceptor} for use with methods like {@link
   * io.grpc.ServerBuilder#intercept(ServerInterceptor)}.
   */
  public ServerInterceptor createServerInterceptor() {
    return new TracingServerInterceptor(
        serverInstrumenter, captureExperimentalSpanAttributes, emitMessageEvents);
  }

  private TracingClientInterceptor newTracingClientInterceptor(
      @Nullable String target, boolean computedTarget) {
    return new TracingClientInterceptor(
        clientInstrumenter,
        propagators,
        captureExperimentalSpanAttributes,
        emitMessageEvents,
        target,
        computedTarget);
  }
}
