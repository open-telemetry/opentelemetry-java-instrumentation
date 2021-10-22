/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import org.eclipse.jetty.client.HttpClient;

/** JettyClientTracing, the Entrypoint for tracing Jetty client. */
public final class JettyClientTracing {

  /** Returns a new {@link JettyClientTracing} configured with the given {@link OpenTelemetry}. */
  public static JettyClientTracing create(OpenTelemetry openTelemetry) {
    JettyClientTracingBuilder builder = builder(openTelemetry);
    return builder.build();
  }

  /**
   * Returns a new {@link JettyClientTracingBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static JettyClientTracingBuilder builder(OpenTelemetry openTelemetry) {
    return new JettyClientTracingBuilder(openTelemetry);
  }

  private final HttpClient httpClient;

  JettyClientTracing(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }
}
