/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

enum RatpackHttpClientAttributesGetter
    implements HttpClientAttributesGetter<RequestSpec, HttpResponse> {
  INSTANCE;

  @Nullable
  @Override
  public String url(RequestSpec requestSpec) {
    return requestSpec.getUri().toString();
  }

  @Override
  public String flavor(RequestSpec requestSpec, @Nullable HttpResponse httpResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Nullable
  @Override
  public String method(RequestSpec requestSpec) {
    return requestSpec.getMethod().getName();
  }

  @Override
  public List<String> requestHeader(RequestSpec requestSpec, String name) {
    return requestSpec.getHeaders().getAll(name);
  }

  @Nullable
  @Override
  public Long requestContentLength(RequestSpec requestSpec, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Nullable
  @Override
  public Long requestContentLengthUncompressed(
      RequestSpec requestSpec, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  public Integer statusCode(RequestSpec requestSpec, HttpResponse httpResponse) {
    return httpResponse.getStatusCode();
  }

  @Nullable
  @Override
  public Long responseContentLength(RequestSpec requestSpec, HttpResponse httpResponse) {
    return null;
  }

  @Nullable
  @Override
  public Long responseContentLengthUncompressed(
      RequestSpec requestSpec, HttpResponse httpResponse) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      RequestSpec requestSpec, HttpResponse httpResponse, String name) {
    return httpResponse.getHeaders().getAll(name);
  }
}
