/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import io.opentelemetry.javaagent.instrumentation.vertx.client.AbstractVertxClientTracer;
import io.vertx.core.http.HttpClientRequest;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nullable;

public class VertxClientTracer extends AbstractVertxClientTracer {
  private static final VertxClientTracer TRACER = new VertxClientTracer();

  public static VertxClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.vertx-http-client-3.0";
  }

  @Override
  protected String method(HttpClientRequest request) {
    return request.method().name();
  }

  @Override
  @Nullable
  protected URI url(HttpClientRequest request) throws URISyntaxException {
    return new URI(request.uri());
  }
}
