/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.HttpVersion;
import org.apache.hc.core5.http.ProtocolVersion;
import org.checkerframework.checker.nullness.qual.Nullable;

public class ApacheHttpClientTracer
    extends HttpClientTracer<ClassicHttpRequest, ClassicHttpRequest, HttpResponse> {

  private static final ApacheHttpClientTracer TRACER = new ApacheHttpClientTracer();

  public static ApacheHttpClientTracer tracer() {
    return TRACER;
  }

  public Context startSpan(Context parentContext, HttpHost host, ClassicHttpRequest request) {
    return startSpan(parentContext, new RequestWithHost(host, request));
  }

  public Context startSpan(Context parentContext, ClassicHttpRequest request) {
    return startSpan(parentContext, request, request);
  }

  @Override
  protected String method(ClassicHttpRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  protected @Nullable String flavor(ClassicHttpRequest request) {
    ProtocolVersion protocolVersion = request.getVersion();
    if (protocolVersion == null) {
      protocolVersion = HttpVersion.HTTP_1_1;
    }
    return protocolVersion.toString();
  }

  @Override
  protected URI url(ClassicHttpRequest request) throws URISyntaxException {
    return request.getUri();
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.getCode();
  }

  @Override
  protected String requestHeader(ClassicHttpRequest request, String name) {
    return header(request, name);
  }

  @Override
  protected String responseHeader(HttpResponse response, String name) {
    return header(response, name);
  }

  @Override
  protected TextMapSetter<ClassicHttpRequest> getSetter() {
    return HttpHeadersInjectAdapter.SETTER;
  }

  private static String header(HttpMessage message, String name) {
    Header header = message.getFirstHeader(name);
    return header != null ? header.getValue() : null;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.apache-httpclient-5.0";
  }
}
