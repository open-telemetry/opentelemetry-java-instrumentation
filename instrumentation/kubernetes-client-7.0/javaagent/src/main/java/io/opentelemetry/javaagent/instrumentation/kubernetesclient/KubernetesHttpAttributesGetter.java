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
  public String method(Request request) {
    return request.method();
  }

  @Override
  public String url(Request request) {
    return request.url().toString();
  }

  @Override
  public List<String> requestHeader(Request request, String name) {
    return request.headers(name);
  }

  @Nullable
  @Override
  public Long requestContentLength(Request request, @Nullable ApiResponse<?> apiResponse) {
    return null;
  }

  @Nullable
  @Override
  public Long requestContentLengthUncompressed(
      Request request, @Nullable ApiResponse<?> apiResponse) {
    return null;
  }

  @Override
  public String flavor(Request request, @Nullable ApiResponse<?> apiResponse) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public Integer statusCode(Request request, ApiResponse<?> apiResponse) {
    return apiResponse.getStatusCode();
  }

  @Nullable
  @Override
  public Long responseContentLength(Request request, ApiResponse<?> apiResponse) {
    return null;
  }

  @Nullable
  @Override
  public Long responseContentLengthUncompressed(Request request, ApiResponse<?> apiResponse) {
    return null;
  }

  @Override
  public List<String> responseHeader(Request request, ApiResponse<?> apiResponse, String name) {
    return apiResponse.getHeaders().getOrDefault(name, emptyList());
  }
}
