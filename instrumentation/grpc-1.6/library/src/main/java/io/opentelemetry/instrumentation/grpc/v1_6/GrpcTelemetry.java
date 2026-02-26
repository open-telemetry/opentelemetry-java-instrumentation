/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannelBuilder;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.Internal;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Entrypoint for instrumenting gRPC servers or clients. */
public final class GrpcTelemetry {

  private static final Logger logger = Logger.getLogger(GrpcTelemetry.class.getName());

  // Reflective access to InternalManagedChannelBuilder.interceptWithTarget (available since gRPC
  // 1.64.0). Uses the public static accessor instead of the protected method on
  // ManagedChannelBuilder to avoid needing setAccessible(true).
  @Nullable private static final Method interceptWithTargetMethod;
  @Nullable private static final Class<?> interceptorFactoryClass;

  static {
    Method method = null;
    Class<?> factoryClass = null;
    try {
      Class<?> internalBuilder = Class.forName("io.grpc.InternalManagedChannelBuilder");
      factoryClass =
          Class.forName("io.grpc.InternalManagedChannelBuilder$InternalInterceptorFactory");
      method =
          internalBuilder.getMethod(
              "interceptWithTarget", ManagedChannelBuilder.class, factoryClass);
    } catch (ClassNotFoundException | NoSuchMethodException e) {
      // gRPC version < 1.64.0, interceptWithTarget not available
    }
    interceptWithTargetMethod = method;
    interceptorFactoryClass = factoryClass;

    Internal.setClientInterceptorFactory(
        (telemetry, target) -> telemetry.newTracingClientInterceptor(target));
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
   * Configures the given {@link ManagedChannelBuilder} with OpenTelemetry tracing instrumentation.
   *
   * <p>On gRPC 1.64.0+, this method automatically captures the channel's target string for
   * populating {@code server.address} and {@code server.port} attributes. On older gRPC versions,
   * it falls back to using the channel's authority.
   *
   * <p>This is the recommended way to instrument a gRPC channel, instead of calling {@link
   * #createClientInterceptor()} and adding the interceptor manually.
   *
   * @param builder the channel builder to configure
   */
  @SuppressWarnings("unchecked")
  public void addClientInterceptor(ManagedChannelBuilder<?> builder) {
    if (interceptWithTargetMethod != null && interceptorFactoryClass != null) {
      try {
        Object factory =
            Proxy.newProxyInstance(
                ManagedChannelBuilder.class.getClassLoader(),
                new Class<?>[] {interceptorFactoryClass},
                (proxy, method, args) -> {
                  if ("newInterceptor".equals(method.getName())) {
                    String target = (String) args[0];
                    return newTracingClientInterceptor(target);
                  }
                  return method.invoke(builder, args);
                });
        interceptWithTargetMethod.invoke(null, builder, factory);
        return;
      } catch (Exception e) {
        logger.log(Level.FINE, "Failed to use interceptWithTarget, falling back", e);
      }
    }

    // Fallback for gRPC < 1.64.0: add interceptor without target info
    builder.intercept(newTracingClientInterceptor(null));
  }

  /**
   * Returns a new {@link ClientInterceptor} for use with methods like {@link
   * io.grpc.ManagedChannelBuilder#intercept(ClientInterceptor...)}.
   *
   * @deprecated Use {@link #addClientInterceptor(ManagedChannelBuilder)} instead.
   */
  @Deprecated
  public ClientInterceptor createClientInterceptor() {
    return newTracingClientInterceptor(null);
  }

  /**
   * Returns a new {@link ServerInterceptor} for use with methods like {@link
   * io.grpc.ServerBuilder#intercept(ServerInterceptor)}.
   */
  public ServerInterceptor createServerInterceptor() {
    return new TracingServerInterceptor(
        serverInstrumenter, captureExperimentalSpanAttributes, emitMessageEvents);
  }

  private TracingClientInterceptor newTracingClientInterceptor(@Nullable String target) {
    return new TracingClientInterceptor(
        clientInstrumenter,
        propagators,
        captureExperimentalSpanAttributes,
        emitMessageEvents,
        target);
  }
}
