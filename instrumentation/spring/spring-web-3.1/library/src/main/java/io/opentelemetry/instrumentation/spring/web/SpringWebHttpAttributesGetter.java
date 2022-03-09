/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.web;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;

enum SpringWebHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, ClientHttpResponse> {
  INSTANCE;

  @Override
  public String method(HttpRequest httpRequest) {
    return httpRequest.getMethod().name();
  }

  @Override
  @Nullable
  public String url(HttpRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  public List<String> requestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeaders().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  public Long requestContentLength(
      HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  @Nullable
  public String flavor(HttpRequest httpRequest, @Nullable ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  public Integer statusCode(HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    try {
      return clientHttpResponse.getStatusCode().value();
    } catch (IOException e) {
      return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }
  }

  @Override
  @Nullable
  public Long responseContentLength(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      HttpRequest httpRequest, ClientHttpResponse clientHttpResponse, String name) {
    return clientHttpResponse.getHeaders().getOrDefault(name, emptyList());
  }
}
