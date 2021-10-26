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

/** Entrypoint for tracing Apache HTTP Client. */
public final class ApacheHttpClientTracing {

  /**
   * Returns a new {@link ApacheHttpClientTracing} configured with the given {@link OpenTelemetry}.
   */
  public static ApacheHttpClientTracing create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link ApacheHttpClientTracingBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static ApacheHttpClientTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new ApacheHttpClientTracingBuilder(openTelemetry);
  }

  private final Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter;
  private final ContextPropagators propagators;

  ApacheHttpClientTracing(
      Instrumenter<ApacheHttpClientRequest, HttpResponse> instrumenter,
      ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  /** Returns a new {@link CloseableHttpClient} with tracing configured. */
  public CloseableHttpClient newHttpClient() {
    return newHttpClientBuilder().build();
  }

  /** Returns a new {@link HttpClientBuilder} to create a client with tracing configured. */
  public HttpClientBuilder newHttpClientBuilder() {
    return new TracingHttpClientBuilder(instrumenter, propagators);
  }
}
