/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.googlehttpclient;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.context.propagation.TextMapPropagator.Setter;
import io.opentelemetry.instrumentation.api.tracer.HttpClientTracer;
import java.net.URI;
import java.net.URISyntaxException;

public class GoogleHttpClientTracer
    extends HttpClientTracer<HttpRequest, HttpHeaders, HttpResponse> {
  public static final GoogleHttpClientTracer TRACER = new GoogleHttpClientTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.google-http-client-1.19";
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

  @Override
  protected Setter<HttpHeaders> getSetter() {
    return HeadersInjectAdapter.SETTER;
  }

  private static String header(HttpHeaders headers, String name) {
    return headers.getFirstHeaderStringValue(name);
  }
}
