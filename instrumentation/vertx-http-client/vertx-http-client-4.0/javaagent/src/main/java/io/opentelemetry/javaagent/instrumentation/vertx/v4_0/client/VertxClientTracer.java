/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

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
    return "io.opentelemetry.vertx-http-client-4.0";
  }

  @Override
  protected String method(HttpClientRequest request) {
    return request.getMethod().name();
  }

  @Override
  @Nullable
  protected URI url(HttpClientRequest request) throws URISyntaxException {
    URI uri = new URI(request.getURI());
    if (!uri.isAbsolute()) {
      uri = new URI(request.absoluteURI());
    }
    return uri;
  }
}
