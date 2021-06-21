/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * JettyClientTracing Designed for use outside of Javaagent. Use the static creation methods to
 * build a Jetty HttpClient that is wrapped with tracing enabled
 */
public class JettyClientTracing {

  private final HttpClient httpClient;

  public HttpClient getHttpClient() {
    return httpClient;
  }

  JettyClientTracing(Instrumenter<Request, Response> instrumenter, HttpClient httpClient) {
    this.httpClient = httpClient;
  }
}
