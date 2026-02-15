/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

/** Entrypoint for instrumenting Apache HTTP Client. */
public final class ApacheHttpClientTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static ApacheHttpClientTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static ApacheHttpClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new ApacheHttpClientTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter;
  private final ContextPropagators propagators;

  ApacheHttpClientTelemetry(
      Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter,
      ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  /** Returns an instrumented HTTP client. */
  public CloseableHttpClient createHttpClient() {
    return createHttpClientBuilder().build();
  }

  /** Returns a builder for creating an instrumented HTTP client. */
  public HttpClientBuilder createHttpClientBuilder() {
    return new TracingHttpClientBuilder(instrumenter, propagators);
  }
}
