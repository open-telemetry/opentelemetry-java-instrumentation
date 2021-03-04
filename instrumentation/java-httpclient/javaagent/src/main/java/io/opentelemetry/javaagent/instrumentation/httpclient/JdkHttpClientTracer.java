/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
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
  private static final JdkHttpClientTracer TRACER = new JdkHttpClientTracer();

  public static JdkHttpClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.java-httpclient";
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
  protected void onResponse(Span span, HttpResponse<?> httpResponse) {
    super.onResponse(span, httpResponse);

    if (httpResponse != null) {
      span.setAttribute(
          SemanticAttributes.HTTP_FLAVOR,
          httpResponse.version() == Version.HTTP_1_1 ? "1.1" : "2.0");
    }
  }

  @Override
  protected TextMapSetter<HttpRequest> getSetter() {
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

    inject(
        Context.current(),
        headerMap,
        (carrier, key, value) -> carrier.put(key, Collections.singletonList(value)));
    headerMap.putAll(original.map());

    return HttpHeaders.of(headerMap, (s, s2) -> true);
  }
}
