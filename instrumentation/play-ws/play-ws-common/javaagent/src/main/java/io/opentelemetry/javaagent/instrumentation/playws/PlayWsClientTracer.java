/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.playws;

import static io.opentelemetry.javaagent.instrumentation.playws.HeadersInjectAdapter.SETTER;

import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;
import play.shaded.ahc.io.netty.handler.codec.http.HttpHeaders;
import play.shaded.ahc.org.asynchttpclient.Request;
import play.shaded.ahc.org.asynchttpclient.Response;

public class PlayWsClientTracer extends HttpClientTracer<Request, Response> {
  private static final PlayWsClientTracer TRACER = new PlayWsClientTracer();

  public static PlayWsClientTracer tracer() {
    return TRACER;
  }

  public HttpClientOperation<Response> startOperation(Request request, HttpHeaders headers) {
    return super.startOperation(request, headers, SETTER);
  }

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
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.play-ws";
  }
}
