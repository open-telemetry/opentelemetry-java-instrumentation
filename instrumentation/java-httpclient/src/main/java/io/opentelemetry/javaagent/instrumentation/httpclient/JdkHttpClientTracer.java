/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap.Depth;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;

public class JdkHttpClientTracer
    extends HttpClientTracer<HttpRequest, HttpRequest, HttpResponse<?>> {
  public static final JdkHttpClientTracer TRACER = new JdkHttpClientTracer();

  public Depth getCallDepth() {
    return CallDepthThreadLocalMap.getCallDepth(HttpClient.class);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.java-httpclient";
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method();
  }

  @Override
  protected URI url(HttpRequest httpRequest) {
    return httpRequest.uri();
  }

  @Override
  protected Integer status(HttpResponse<?> httpResponse) {
    return httpResponse.statusCode();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().firstValue(name).orElse(null);
  }

  @Override
  protected String responseHeader(HttpResponse<?> httpResponse, String name) {
    return httpResponse.headers().firstValue(name).orElse(null);
  }

  @Override
  protected Span onResponse(Span span, HttpResponse<?> httpResponse) {
    span = super.onResponse(span, httpResponse);

    if (httpResponse != null) {
      span.setAttribute(
          SemanticAttributes.HTTP_FLAVOR,
          httpResponse.version() == Version.HTTP_1_1 ? "1.1" : "2.0");
    }

    return span;
  }

  @Override
  protected Setter<HttpRequest> getSetter() {
    return HttpHeadersInjectAdapter.SETTER;
  }

  @Override
  protected Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof CompletionException) {
      return throwable.getCause();
    }
    return super.unwrapThrowable(throwable);
  }

  public HttpHeaders inject(HttpHeaders original) {
    Map<String, List<String>> headerMap = new HashMap<>();

    OpenTelemetry.getGlobalPropagators()
        .getTextMapPropagator()
        .inject(
            Context.current(),
            headerMap,
            (carrier, key, value) -> carrier.put(key, Collections.singletonList(value)));
    headerMap.putAll(original.map());

    return HttpHeaders.of(headerMap, (s, s2) -> true);
  }
}
