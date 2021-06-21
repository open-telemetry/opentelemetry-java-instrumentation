/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/** JettyClientTracing, the Entrypoint for tracing Jetty client. */
public final class JettyClientTracing {

  private final HttpClient httpClient;

  public static JettyClientTracing create(OpenTelemetry openTelemetry) {
    JettyClientTracingBuilder builder = newBuilder(openTelemetry);
    return builder.build();
  }

  public static JettyClientTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new JettyClientTracingBuilder(openTelemetry);
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }

  JettyClientTracing(Instrumenter<Request, Response> instrumenter, HttpClient httpClient) {
    this.httpClient = httpClient;
  }
}
