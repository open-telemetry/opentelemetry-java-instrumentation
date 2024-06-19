/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.api.OpenTelemetry;
import org.eclipse.jetty.client.HttpClient;

/** Entrypoint for instrumenting Jetty client. */
public final class JettyClientTelemetry {

  /** Returns a new {@link JettyClientTelemetry} configured with the given {@link OpenTelemetry}. */
  public static JettyClientTelemetry create(OpenTelemetry openTelemetry) {
    JettyClientTelemetryBuilder builder = builder(openTelemetry);
    return builder.build();
  }

  /**
   * Returns a new {@link JettyClientTelemetryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static JettyClientTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JettyClientTelemetryBuilder(openTelemetry);
  }

  private final HttpClient httpClient;

  JettyClientTelemetry(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public HttpClient getHttpClient() {
    return httpClient;
  }
}
