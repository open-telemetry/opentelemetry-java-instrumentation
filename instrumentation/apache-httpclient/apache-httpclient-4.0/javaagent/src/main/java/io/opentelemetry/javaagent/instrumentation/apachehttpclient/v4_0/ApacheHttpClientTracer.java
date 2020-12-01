/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.HttpHeadersInjectAdapter.SETTER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import java.net.URI;
import org.apache.http.Header;
import org.apache.http.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ApacheHttpClientTracer
    extends HttpClientTracer<HttpUriRequest, HttpUriRequest, HttpResponse> {

  private static final ApacheHttpClientTracer TRACER = new ApacheHttpClientTracer();

  public static ApacheHttpClientTracer tracer() {
    return TRACER;
  }

  public CallDepth getCallDepth() {
    return CallDepthThreadLocalMap.getCallDepth(HttpClient.class);
  }

  @Override
  protected String method(HttpUriRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected @Nullable String flavor(HttpUriRequest httpUriRequest) {
    return httpUriRequest.getProtocolVersion().toString();
  }

  @Override
  protected URI url(HttpUriRequest request) {
    return request.getURI();
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.getStatusLine().getStatusCode();
  }

  @Override
  protected String requestHeader(HttpUriRequest request, String name) {
    return header(request, name);
  }

  @Override
  protected String responseHeader(HttpResponse response, String name) {
    return header(response, name);
  }

  @Override
  protected Setter<HttpUriRequest> getSetter() {
    return SETTER;
  }

  private static String header(HttpMessage message, String name) {
    Header header = message.getFirstHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.apache-httpclient";
  }

  /** This method is overridden to allow other classes in this package to call it. */
  @Override
  protected Span onResponse(Span span, HttpResponse httpResponse) {
    return super.onResponse(span, httpResponse);
  }
}
