/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v2_0;

import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap.Depth;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.StatusLine;
import org.apache.commons.httpclient.URIException;

public class CommonsHttpClientTracer extends HttpClientTracer<HttpMethod, HttpMethod, HttpMethod> {
  public static final CommonsHttpClientTracer TRACER = new CommonsHttpClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.apache-httpclient-2.0";
  }

  public Depth getCallDepth() {
    return CallDepthThreadLocalMap.getCallDepth(HttpClient.class);
  }

  @Override
  protected String method(HttpMethod httpMethod) {
    return httpMethod.getName();
  }

  @Override
  protected URI url(HttpMethod httpMethod) throws URISyntaxException {
    try {
      //  org.apache.commons.httpclient.URI -> java.net.URI
      return new URI(httpMethod.getURI().toString());
    } catch (URIException e) {
      throw new URISyntaxException("", e.getMessage());
    }
  }

  @Override
  protected Integer status(HttpMethod httpMethod) {
    StatusLine statusLine = httpMethod.getStatusLine();
    return statusLine == null ? null : statusLine.getStatusCode();
  }

  @Override
  protected String requestHeader(HttpMethod httpMethod, String name) {
    Header header = httpMethod.getRequestHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected String responseHeader(HttpMethod httpMethod, String name) {
    Header header = httpMethod.getResponseHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected Setter<HttpMethod> getSetter() {
    return HttpHeadersInjectAdapter.SETTER;
  }
}
