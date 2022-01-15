/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import ratpack.exec.ExecInitializer;
import ratpack.exec.ExecInterceptor;
import ratpack.handling.HandlerDecorator;
import ratpack.http.Request;
import ratpack.http.Response;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;
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
  private final OpenTelemetryHttpClient httpClientInstrumenter;

  RatpackTracing(
      Instrumenter<Request, Response> serverInstrumenter,
      Instrumenter<RequestSpec, HttpResponse> clientInstrumenter) {
    serverHandler = new OpenTelemetryServerHandler(serverInstrumenter);
    httpClientInstrumenter = new OpenTelemetryHttpClient(clientInstrumenter);
  }

  /** Returns instance of {@link OpenTelemetryServerHandler} to support Ratpack Registry binding. */
  public OpenTelemetryServerHandler getOpenTelemetryServerHandler() {
    return serverHandler;
  }

  /** Returns instance of {@link ExecInterceptor} to support Ratpack Registry binding. */
  public ExecInterceptor getOpenTelemetryExecInterceptor() {
    return OpenTelemetryExecInterceptor.INSTANCE;
  }

  /** Returns instance of {@link ExecInitializer} to support Ratpack Registry binding. */
  public ExecInitializer getOpenTelemetryExecInitializer() {
    return OpenTelemetryExecInitializer.INSTANCE;
  }

  /** Configures the {@link RegistrySpec} with OpenTelemetry. */
  public void configureServerRegistry(RegistrySpec registry) {
    registry.add(HandlerDecorator.prepend(serverHandler));
    registry.add(OpenTelemetryExecInterceptor.INSTANCE);
    registry.add(OpenTelemetryExecInitializer.INSTANCE);
  }

  /** Returns instrumented instance of {@link HttpClient} with OpenTelemetry. */
  public HttpClient instrumentedHttpClient(HttpClient httpClient) throws Exception {
    return httpClientInstrumenter.instrument(httpClient);
  }
}
