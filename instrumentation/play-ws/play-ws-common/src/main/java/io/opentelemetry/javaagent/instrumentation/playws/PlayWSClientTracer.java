/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.playws;

import static io.opentelemetry.instrumentation.auto.playws.HeadersInjectAdapter.SETTER;

import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

public class PlayWSClientTracer extends HttpClientTracer<Request, HttpHeaders, Response> {
  public static final PlayWSClientTracer TRACER = new PlayWSClientTracer();

  @Override
  protected String method(Request request) {
    return request.getMethod();
  }

  @Override
  protected URI url(Request request) throws URISyntaxException {
    return request.getUri().toJavaNetURI();
  }

  @Override
  protected Integer status(Response response) {
    return response.getStatusCode();
  }

  @Override
  protected String requestHeader(Request request, String name) {
    return request.getHeaders().get(name);
  }

  @Override
  protected String responseHeader(Response response, String name) {
    return response.getHeaders().get(name);
  }

  @Override
  protected Setter<HttpHeaders> getSetter() {
    return SETTER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.play-ws-2.1";
  }
}
