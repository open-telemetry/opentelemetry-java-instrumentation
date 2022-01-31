/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

class AwsSdkHttpAttributesGetter
    implements HttpClientAttributesGetter<ExecutionAttributes, SdkHttpResponse> {

  @Override
  public String url(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.getUri().toString();
  }

  @Override
  @Nullable
  public String flavor(ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public String method(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.method().name();
  }

  @Override
  public List<String> requestHeader(ExecutionAttributes request, String name) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    List<String> value = httpRequest.headers().get(name);
    return value == null ? emptyList() : value;
  }

  @Override
  @Nullable
  public Long requestContentLength(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }

  @Override
  public Integer statusCode(ExecutionAttributes request, SdkHttpResponse response) {
    return response.statusCode();
  }

  @Override
  @Nullable
  public Long responseContentLength(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      ExecutionAttributes request, SdkHttpResponse response, String name) {
    List<String> value = response.headers().get(name);
    return value == null ? emptyList() : value;
  }
}
