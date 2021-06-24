/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import org.eclipse.jetty.client.HttpClient;

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

  JettyClientTracing(HttpClient httpClient) {
    this.httpClient = httpClient;
  }
}
