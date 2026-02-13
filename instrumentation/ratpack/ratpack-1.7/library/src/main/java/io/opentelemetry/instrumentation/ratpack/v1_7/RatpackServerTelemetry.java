/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryExecInitializer;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryExecInterceptor;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryServerHandler;
import ratpack.exec.ExecInitializer;
import ratpack.exec.ExecInterceptor;
import ratpack.handling.Handler;
import ratpack.handling.HandlerDecorator;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.registry.RegistrySpec;

/**
 * Entrypoint for instrumenting Ratpack server.
 *
 * <p>To apply OpenTelemetry instrumentation to a server, configure the {@link RegistrySpec} using
 * {@link #configureRegistry(RegistrySpec)}.
 *
 * <pre>{@code
 * RatpackServerTelemetry telemetry = RatpackServerTelemetry.create(OpenTelemetrySdk.builder()
 *   ...
 *   .build());
 * RatpackServer.start(server -> {
 *   server.registryOf(telemetry::configureRegistry);
 *   server.handlers(chain -> ...);
 * });
 * }</pre>
 */
public final class RatpackServerTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static RatpackServerTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static RatpackServerTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new RatpackServerTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<Request, Response> instrumenter;

  RatpackServerTelemetry(Instrumenter<Request, Response> instrumenter) {
    this.instrumenter = instrumenter;
  }

  /** Creates a new {@link Handler} to support Ratpack Registry binding. */
  public Handler createHandler() {
    return new OpenTelemetryServerHandler(instrumenter);
  }

  /** Creates an {@link ExecInterceptor} instance to support Ratpack Registry binding. */
  public ExecInterceptor createExecInterceptor() {
    return OpenTelemetryExecInterceptor.INSTANCE;
  }

  /** Creates an {@link ExecInitializer} instance to support Ratpack Registry binding. */
  public ExecInitializer createExecInitializer() {
    return OpenTelemetryExecInitializer.INSTANCE;
  }

  /** Configures the {@link RegistrySpec} to produce telemetry. */
  public void configureRegistry(RegistrySpec registry) {
    registry.add(HandlerDecorator.prepend(createHandler()));
    registry.add(createExecInterceptor());
    registry.add(createExecInitializer());
  }
}
