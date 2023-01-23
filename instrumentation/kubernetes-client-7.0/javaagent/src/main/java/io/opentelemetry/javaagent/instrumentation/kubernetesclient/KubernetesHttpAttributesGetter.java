/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kubernetesclient;

import static java.util.Collections.emptyList;

import io.kubernetes.client.openapi.ApiResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import okhttp3.Request;

class KubernetesHttpAttributesGetter
    implements HttpClientAttributesGetter<Request, ApiResponse<?>> {

  @Override
  public String getMethod(Request request) {
    return request.method();
  }

  @Override
  public String getUrl(Request request) {
    return request.url().toString();
  }

  @Override
  public List<String> getRequestHeader(Request request, String name) {
    return request.headers(name);
  }

  @Override
  public String getFlavor(Request request, @Nullable ApiResponse<?> apiResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public Integer getStatusCode(
      Request request, ApiResponse<?> apiResponse, @Nullable Throwable error) {
    return apiResponse.getStatusCode();
  }

  @Override
  public List<String> getResponseHeader(Request request, ApiResponse<?> apiResponse, String name) {
    return apiResponse.getHeaders().getOrDefault(name, emptyList());
  }
}
