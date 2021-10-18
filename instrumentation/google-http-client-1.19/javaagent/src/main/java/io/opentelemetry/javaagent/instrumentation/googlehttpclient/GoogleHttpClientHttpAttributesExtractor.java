/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

final class GoogleHttpClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<HttpRequest, HttpResponse> {

  @Override
  @Nullable
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getRequestMethod();
  }

  @Override
  protected String url(HttpRequest httpRequest) {
    return httpRequest.getUrl().build();
  }

  @Override
  protected List<String> requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeaders().getHeaderStringValues(name);
  }

  @Override
  @Nullable
  protected Long requestContentLength(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected String flavor(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  @Nullable
  protected Integer statusCode(HttpRequest httpRequest, HttpResponse httpResponse) {
    return httpResponse.getStatusCode();
  }

  @Override
  @Nullable
  protected Long responseContentLength(HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpRequest httpRequest, HttpResponse httpResponse, String name) {
    return httpResponse.getHeaders().getHeaderStringValues(name);
  }
}
