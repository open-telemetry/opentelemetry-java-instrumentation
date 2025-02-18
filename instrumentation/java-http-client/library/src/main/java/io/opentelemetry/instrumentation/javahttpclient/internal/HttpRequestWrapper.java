/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpclient.internal;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.time.Duration;
import java.util.Optional;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class HttpRequestWrapper extends HttpRequest {
  private final HttpRequest request;
  private final HttpHeaders headers;

  HttpRequestWrapper(HttpRequest request, HttpHeaders headers) {
    this.request = request;
    this.headers = headers;
  }

  @Override
  public Optional<BodyPublisher> bodyPublisher() {
    return request.bodyPublisher();
  }

  @Override
  public String method() {
    return request.method();
  }

  @Override
  public Optional<Duration> timeout() {
    return request.timeout();
  }

  @Override
  public boolean expectContinue() {
    return request.expectContinue();
  }

  @Override
  public URI uri() {
    return request.uri();
  }

  @Override
  public Optional<HttpClient.Version> version() {
    return request.version();
  }

  @Override
  public HttpHeaders headers() {
    return headers;
  }
}
