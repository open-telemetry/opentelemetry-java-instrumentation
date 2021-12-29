/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.client;

import io.opentelemetry.instrumentation.api.instrumenter.http.CapturedHttpHeaders;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import java.util.List;
import javax.annotation.Nullable;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

final class RatpackHttpClientAttributesExtractor
    extends HttpClientAttributesExtractor<RequestSpec, HttpResponse> {

  RatpackHttpClientAttributesExtractor(CapturedHttpHeaders capturedHttpHeaders) {
    super(capturedHttpHeaders);
  }

  @Nullable
  @Override
  protected String url(RequestSpec requestSpec) {
    return requestSpec.getUri().toString();
  }

  @Nullable
  @Override
  protected String flavor(RequestSpec requestSpec, @Nullable HttpResponse httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Nullable
  @Override
  protected String method(RequestSpec requestSpec) {
    return requestSpec.getMethod().getName();
  }

  @Override
  protected List<String> requestHeader(RequestSpec requestSpec, String name) {
    return requestSpec.getHeaders().getAll(name);
  }

  @Nullable
  @Override
  protected Long requestContentLength(
      RequestSpec requestSpec, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Nullable
  @Override
  protected Long requestContentLengthUncompressed(
      RequestSpec requestSpec, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Nullable
  @Override
  protected Integer statusCode(RequestSpec requestSpec, HttpResponse httpResponse) {
    return httpResponse.getStatusCode();
  }

  @Nullable
  @Override
  protected Long responseContentLength(RequestSpec requestSpec, HttpResponse httpResponse) {
    return null;
  }

  @Nullable
  @Override
  protected Long responseContentLengthUncompressed(
      RequestSpec requestSpec, HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      RequestSpec requestSpec, HttpResponse httpResponse, String name) {
    return httpResponse.getHeaders().getAll(name);
  }
}
