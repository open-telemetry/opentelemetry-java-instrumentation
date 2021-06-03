/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2;

import static io.opentelemetry.javaagent.instrumentation.jetty.httpclient.v9_2.RequestHeaderInjectorAdapter.SETTER;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;

public class JettyHttpClient9Tracer extends HttpClientTracer<Request, Request, Response> {
  private static final JettyHttpClient9Tracer TRACER = new JettyHttpClient9Tracer();

  public static final JettyHttpClient9Tracer tracer() {
    return TRACER;
  }

  private JettyHttpClient9Tracer() {
    super(NetPeerAttributes.INSTANCE);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jetty-httpclient-9.0";
  }

  @Override
  protected String method(Request request) {
    return request.getMethod();
  }

  @Override
  protected @Nullable URI url(Request request) throws URISyntaxException {
    return request.getURI();
  }

  @Override
  protected @Nullable Integer status(Response response) {
    return response.getStatus();
  }

  @Override
  protected @Nullable String requestHeader(Request request, String name) {
    HttpFields headers = request.getHeaders();
    return extractHeader(headers, name);
  }

  @Override
  protected @Nullable String responseHeader(Response response, String name) {
    HttpFields headers = response.getHeaders();
    return extractHeader(headers, name);
  }

  /** Override so can be called from interceptor code */
  @Override
  protected void onRequest(Span span, Request request) {

    HttpField agentField = request.getHeaders().getField(USER_AGENT);
    span.setAttribute(SemanticAttributes.HTTP_USER_AGENT, agentField.getValue());

    super.onRequest(span, request);
  }

  protected void updateSpanName(Span span, Request request) {
    span.updateName(super.spanNameForRequest(request));
  }

  @Override
  protected TextMapSetter<Request> getSetter() {
    return SETTER;
  }

  private @Nullable String extractHeader(HttpFields headers, String name) {

    String headerVal = null;
    if (headers != null) {
      headerVal = headers.containsKey(name) ? headers.get(name) : null;
    }
    return headerVal;
  }
}
