/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.client;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import ratpack.exec.ExecInitializer;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/** Entrypoint for tracing OkHttp clients. */
public final class RatpackHttpTracing {

  /** Returns a new {@link RatpackHttpTracing} configured with the given {@link OpenTelemetry}. */
  public static RatpackHttpTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link RatpackHttpTracingBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static RatpackHttpTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new RatpackHttpTracingBuilder(openTelemetry);
  }

  private final Instrumenter<RequestSpec, HttpResponse> instrumenter;

  RatpackHttpTracing(Instrumenter<RequestSpec, HttpResponse> instrumenter) {
    this.instrumenter = instrumenter;
  }

  public HttpClient instrumentedHttpClient(HttpClient httpClient) throws Exception {
    return new OpenTelemetryHttpClient(instrumenter).instrument(httpClient);
  }

  /** Returns instance of {@link ExecInitializer} to support Ratpack Registry binding. */
  public ExecInitializer getOpenTelemetryExecInitializer() {
    return OpenTelemetryExecInitializer.INSTANCE;
  }
}
