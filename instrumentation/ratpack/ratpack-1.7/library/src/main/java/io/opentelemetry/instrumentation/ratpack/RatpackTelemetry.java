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
 * Entrypoint for instrumenting Ratpack servers. To apply OpenTelemetry to a server, configure the
 * {@link RegistrySpec} using {@link #configureServerRegistry(RegistrySpec)}.
 *
 * <pre>{@code
 * RatpackTelemetry telemetry = RatpackTelemetry.create(OpenTelemetrySdk.builder()
 *   ...
 *   .build());
 * RatpackServer.start(server -> {
 *   server.registryOf(telemetry::configureServerRegistry);
 *   server.handlers(chain -> ...);
 * });
 * }</pre>
 */
public final class RatpackTelemetry {

  /** Returns a new {@link RatpackTelemetry} configured with the given {@link OpenTelemetry}. */
  public static RatpackTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link RatpackTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static RatpackTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new RatpackTelemetryBuilder(openTelemetry);
  }

  private final OpenTelemetryServerHandler serverHandler;
  private final OpenTelemetryHttpClient httpClientInstrumenter;

  RatpackTelemetry(
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
  public HttpClient instrumentHttpClient(HttpClient httpClient) throws Exception {
    return httpClientInstrumenter.instrument(httpClient);
  }
}
