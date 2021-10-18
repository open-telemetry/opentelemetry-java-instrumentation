/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

final class SpringWebHttpAttributesExtractor
    extends HttpClientAttributesExtractor<HttpRequest, ClientHttpResponse> {

  SpringWebHttpAttributesExtractor(CapturedHttpHeaders capturedHttpHeaders) {
    super(capturedHttpHeaders);
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  @Nullable
  protected String url(HttpRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  protected List<String> requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeaders().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  protected Long requestContentLength(
      HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  @Nullable
  protected String flavor(
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
  @Nullable
  protected Long responseContentLength(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, String name) {
    return clientHttpResponse.getHeaders().getOrDefault(name, emptyList());
  }
}
