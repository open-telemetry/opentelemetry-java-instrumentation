/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.ratpack.v1_7.internal.OpenTelemetryHttpClient;
import ratpack.http.client.HttpClient;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/**
 * Entrypoint for instrumenting Ratpack http client.
 *
 * <p>To apply OpenTelemetry instrumentation to a http client, wrap the {@link HttpClient} using
 * {@link #instrument(HttpClient)}.
 *
 * <pre>{@code
 * RatpackClientTelemetry telemetry = RatpackClientTelemetry.create(OpenTelemetrySdk.builder()
 *   ...
 *   .build());
 * HttpClient instrumentedHttpClient = telemetry.instrument(httpClient);
 * }</pre>
 */
public final class RatpackClientTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static RatpackClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static RatpackClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new RatpackClientTelemetryBuilder(openTelemetry);
  }

  private final OpenTelemetryHttpClient httpClientInstrumenter;

  RatpackClientTelemetry(Instrumenter<RequestSpec, HttpResponse> clientInstrumenter) {
    httpClientInstrumenter = new OpenTelemetryHttpClient(clientInstrumenter);
  }

  /** Returns an instrumented wrapper for the given {@link HttpClient}. */
  public HttpClient instrument(HttpClient httpClient) throws Exception {
    return httpClientInstrumenter.instrument(httpClient);
  }
}
