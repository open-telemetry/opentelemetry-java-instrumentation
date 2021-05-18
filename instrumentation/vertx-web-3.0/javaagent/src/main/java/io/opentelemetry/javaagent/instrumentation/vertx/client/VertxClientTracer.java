/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;

public class VertxClientTracer
    extends HttpClientTracer<HttpClientRequest, HttpClientRequest, HttpClientResponse> {
  private static final VertxClientTracer TRACER = new VertxClientTracer();

  public static VertxClientTracer tracer() {
    return TRACER;
  }

  public VertxClientTracer() {
    super(NetPeerAttributes.INSTANCE);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.vertx-core-3.0";
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

  @Override
  @Nullable
  protected Integer status(HttpClientResponse response) {
    return response.statusCode();
  }

  @Override
  @Nullable
  protected String requestHeader(HttpClientRequest request, String name) {
    return request.headers().get(name);
  }

  @Override
  @Nullable
  protected String responseHeader(HttpClientResponse response, String name) {
    return response.getHeader(name);
  }

  @Override
  protected TextMapSetter<HttpClientRequest> getSetter() {
    return Propagator.INSTANCE;
  }

  private static class Propagator implements TextMapSetter<HttpClientRequest> {
    private static final Propagator INSTANCE = new Propagator();

    @Override
    public void set(HttpClientRequest carrier, String key, String value) {
      if (carrier != null) {
        carrier.putHeader(key, value);
      }
    }
  }
}
