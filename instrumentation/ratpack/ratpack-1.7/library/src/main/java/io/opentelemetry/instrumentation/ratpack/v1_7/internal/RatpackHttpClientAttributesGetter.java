/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.v1_7.internal;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
enum RatpackHttpClientAttributesGetter
    implements HttpClientAttributesGetter<RequestSpec, HttpResponse> {
  INSTANCE;

  @Nullable
  @Override
  public String getUrlFull(RequestSpec requestSpec) {
    return requestSpec.getUri().toString();
  }

  @Nullable
  @Override
  public String getHttpRequestMethod(RequestSpec requestSpec) {
    return requestSpec.getMethod().getName();
  }

  @Override
  public List<String> getHttpRequestHeader(RequestSpec requestSpec, String name) {
    return requestSpec.getHeaders().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      RequestSpec requestSpec, HttpResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      RequestSpec requestSpec, HttpResponse httpResponse, String name) {
    return httpResponse.getHeaders().getAll(name);
  }

  @Override
  @Nullable
  public String getServerAddress(RequestSpec request) {
    return request.getUri().getHost();
  }

  @Override
  public Integer getServerPort(RequestSpec request) {
    return request.getUri().getPort();
  }
}
