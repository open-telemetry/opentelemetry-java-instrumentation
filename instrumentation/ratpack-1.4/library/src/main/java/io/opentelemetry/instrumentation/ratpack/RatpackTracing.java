/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import ratpack.exec.ExecInterceptor;
import ratpack.handling.HandlerDecorator;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.registry.RegistrySpec;

/**
 * Entrypoint for tracing Ratpack servers. To apply OpenTelemetry to a server, configure the {@link
 * RegistrySpec} using {@link #configureServerRegistry(RegistrySpec)}.
 *
 * <pre>{@code
 * RatpackTracing tracing = RatpackTracing.create(OpenTelemetrySdk.builder()
 *   ...
 *   .build());
 * RatpackServer.start(server -> {
 *   server.registryOf(tracing::configureServerRegistry);
 *   server.handlers(chain -> ...);
 * });
 * }</pre>
 */
public final class RatpackTracing {

  /** Returns a new {@link RatpackTracing} configured with the given {@link OpenTelemetry}. */
  public static RatpackTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link RatpackTracingBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static RatpackTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new RatpackTracingBuilder(openTelemetry);
  }

  private final OpenTelemetryServerHandler serverHandler;

  RatpackTracing(Instrumenter<Request, Response> serverInstrumenter) {
    serverHandler = new OpenTelemetryServerHandler(serverInstrumenter);
  }

  public OpenTelemetryServerHandler getOpenTelemetryServerHandler() {
    return serverHandler;
  }

  public ExecInterceptor getOpenTelemetryExecInterceptor() {
    return OpenTelemetryExecInterceptor.INSTANCE;
  }

  /** Configures the {@link RegistrySpec} with OpenTelemetry. */
  public void configureServerRegistry(RegistrySpec registry) {
    registry.add(HandlerDecorator.prepend(serverHandler));
    registry.add(OpenTelemetryExecInterceptor.INSTANCE);
  }
}
