/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient.v7_0;

import static java.util.Collections.emptyList;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Request;

class KubernetesHttpAttributesGetter
    implements HttpClientAttributesGetter<Request, ApiResponse<?>> {

  @Override
  public String getHttpRequestMethod(Request request) {
    return request.method();
  }

  @Override
  public String getUrlFull(Request request) {
    return request.url().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return request.headers(name);
  }

  @Override
  @Nullable
  public Integer getHttpResponseStatusCode(
      Request request, @Nullable ApiResponse<?> apiResponse, @Nullable Throwable error) {
    return apiResponse == null ? null : apiResponse.getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      Request request, @Nullable ApiResponse<?> apiResponse, String name) {
    return apiResponse == null
        ? emptyList()
        : apiResponse.getHeaders().getOrDefault(name, emptyList());
  }

  @Override
  public String getServerAddress(Request request) {
    return request.url().host();
  }

  @Override
  public Integer getServerPort(Request request) {
    return request.url().port();
  }
}
