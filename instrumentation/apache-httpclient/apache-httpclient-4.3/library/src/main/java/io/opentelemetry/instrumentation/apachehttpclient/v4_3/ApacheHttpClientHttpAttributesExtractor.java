/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import org.apache.http.HttpResponse;
import org.checkerframework.checker.nullness.qual.Nullable;

final class ApacheHttpClientHttpAttributesExtractor
    extends HttpClientAttributesExtractor<ApacheHttpClientRequest, HttpResponse> {

  @Override
  protected String method(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  protected String url(ApacheHttpClientRequest request) {
    return request.getUrl();
  }

  @Override
  @Nullable
  protected String userAgent(ApacheHttpClientRequest request) {
    return request.getHeader("User-Agent");
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
  protected Integer statusCode(ApacheHttpClientRequest request, HttpResponse response) {
    return response.getStatusLine().getStatusCode();
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
}
