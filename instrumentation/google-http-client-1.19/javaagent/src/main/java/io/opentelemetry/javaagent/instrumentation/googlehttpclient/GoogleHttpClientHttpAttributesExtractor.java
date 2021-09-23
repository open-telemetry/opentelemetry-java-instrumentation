/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

final class GoogleHttpClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<HttpRequest, HttpResponse> {

  @Override
  protected @Nullable String method(HttpRequest httpRequest) {
    return httpRequest.getRequestMethod();
  }

  @Override
  protected String url(HttpRequest httpRequest) {
    return httpRequest.getUrl().build();
  }

  @Override
  protected String target(HttpRequest httpRequest) {
    return httpRequest.getUrl().buildRelativeUrl();
  }

  @Override
  protected String host(HttpRequest httpRequest) {
    return httpRequest.getUrl().getHost();
  }

  @Override
  protected String scheme(HttpRequest httpRequest) {
    return httpRequest.getUrl().getScheme();
  }

  @Override
  protected @Nullable String userAgent(HttpRequest httpRequest) {
    return httpRequest.getHeaders().getUserAgent();
  }

  @Override
  protected @Nullable Long requestContentLength(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected String flavor(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  protected @Nullable Integer statusCode(HttpRequest httpRequest, HttpResponse httpResponse) {
    return httpResponse.getStatusCode();
  }

  @Override
  protected @Nullable Long responseContentLength(
      HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }
}
