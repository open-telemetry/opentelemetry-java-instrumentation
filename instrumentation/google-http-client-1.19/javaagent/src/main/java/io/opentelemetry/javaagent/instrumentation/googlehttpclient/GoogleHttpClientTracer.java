/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import static io.opentelemetry.javaagent.instrumentation.googlehttpclient.HeadersInjectAdapter.SETTER;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import io.opentelemetry.instrumentation.api.tracer.Operation;
import java.net.URI;
import java.net.URISyntaxException;

public class GoogleHttpClientTracer extends HttpClientTracer<HttpRequest, HttpResponse> {
  private static final GoogleHttpClientTracer TRACER = new GoogleHttpClientTracer();

  public static GoogleHttpClientTracer tracer() {
    return TRACER;
  }

  public Operation<HttpResponse> startOperation(HttpRequest request) {
    return startOperation(request, request.getHeaders(), SETTER);
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.google-http-client";
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getRequestMethod();
  }

  @Override
  protected URI url(HttpRequest httpRequest) throws URISyntaxException {
    // Google uses %20 (space) instead of "+" for spaces in the fragment
    // Add "+" back for consistency with the other http client instrumentations
    String url = httpRequest.getUrl().build();
    String fixedUrl = url.replaceAll("%20", "+");
    return new URI(fixedUrl);
  }

  @Override
  protected Integer status(HttpResponse httpResponse) {
    return httpResponse.getStatusCode();
  }

  @Override
  protected String requestHeader(HttpRequest httpRequest, String name) {
    return header(httpRequest.getHeaders(), name);
  }

  @Override
  protected String responseHeader(HttpResponse httpResponse, String name) {
    return header(httpResponse.getHeaders(), name);
  }

  private static String header(HttpHeaders headers, String name) {
    return headers.getFirstHeaderStringValue(name);
  }
}
