/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static io.opentelemetry.javaagent.instrumentation.httpclient.HttpHeadersInjectAdapter.SETTER;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.attributes.SemanticAttributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
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

public class JdkHttpClientTracer extends HttpClientTracer<HttpRequest, HttpResponse<?>> {
  private static final JdkHttpClientTracer TRACER = new JdkHttpClientTracer();

  public static JdkHttpClientTracer tracer() {
    return TRACER;
  }

  public HttpClientOperation<HttpResponse<?>> startOperation(HttpRequest request) {
    return super.startOperation(request, SETTER);
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
  protected void onResponse(Context context, HttpResponse<?> httpResponse) {
    super.onResponse(context, httpResponse);
    String flavor = httpResponse.version() == Version.HTTP_1_1 ? "1.1" : "2.0";
    Span.fromContext(context).setAttribute(SemanticAttributes.HTTP_FLAVOR, flavor);
  }

  @Override
  protected void onException(Span span, Throwable throwable) {
    super.onException(span, unwrapThrowable(throwable));
  }

  private static Throwable unwrapThrowable(Throwable throwable) {
    if (throwable instanceof CompletionException) {
      return throwable.getCause();
    }
    return throwable;
  }

  // TODO (trask) need to pass in Operation here so that injection will not occur when Operation is
  //  a no-op
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
