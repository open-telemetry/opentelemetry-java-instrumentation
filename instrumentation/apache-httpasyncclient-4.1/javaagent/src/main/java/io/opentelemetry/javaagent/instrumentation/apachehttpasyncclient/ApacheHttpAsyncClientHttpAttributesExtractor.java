/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.api.config.HttpHeadersConfig;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpAsyncClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  ApacheHttpAsyncClientHttpAttributesExtractor() {
    super(HttpHeadersConfig.capturedClientHeaders());
  }

  @Override
  protected String method(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  protected String url(ApacheHttpClientRequest request) {
    return request.getUrl();
  }

  @Override
  protected List<String> requestHeader(ApacheHttpClientRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  @Nullable
  protected Long requestContentLength(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Integer statusCode(ApacheHttpClientRequest request, HttpResponse response) {
    StatusLine statusLine = response.getStatusLine();
    return statusLine != null ? statusLine.getStatusCode() : null;
  }

  @Override
  @Nullable
  protected String flavor(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getFlavor();
  }

  @Override
  @Nullable
  protected Long responseContentLength(ApacheHttpClientRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      ApacheHttpClientRequest request, HttpResponse response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      ApacheHttpClientRequest request, HttpResponse response, String name) {
    return Arrays.stream(response.getHeaders(name))
        .map(Header::getValue)
        .collect(Collectors.toList());
  }
}
