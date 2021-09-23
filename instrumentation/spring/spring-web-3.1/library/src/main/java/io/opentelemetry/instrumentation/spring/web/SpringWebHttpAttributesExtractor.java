/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

final class SpringWebHttpAttributesExtractor
    extends HttpClientAttributesExtractor<HttpRequest, ClientHttpResponse> {
  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  protected @Nullable String url(HttpRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  protected @Nullable String target(HttpRequest httpRequest) {
    return null;
  }

  @Override
  protected @Nullable String host(HttpRequest httpRequest) {
    return httpRequest.getHeaders().getFirst("host");
  }

  @Override
  protected @Nullable String scheme(HttpRequest httpRequest) {
    return httpRequest.getURI().getScheme();
  }

  @Override
  protected @Nullable String userAgent(HttpRequest httpRequest) {
    // using lowercase header name intentionally to ensure extraction is not case-sensitive
    return httpRequest.getHeaders().getFirst("user-agent");
  }

  @Override
  protected @Nullable Long requestContentLength(
      HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  protected @Nullable String flavor(
      HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    try {
      return clientHttpResponse.getStatusCode().value();
    } catch (IOException e) {
      return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
  }

  @Override
  protected @Nullable Long responseContentLength(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    return null;
  }
}
