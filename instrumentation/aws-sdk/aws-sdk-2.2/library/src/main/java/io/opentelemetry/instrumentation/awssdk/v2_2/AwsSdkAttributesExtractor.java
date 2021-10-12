/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

class AwsSdkAttributesExtractor
    extends HttpClientAttributesExtractor<ExecutionAttributes, SdkHttpResponse> {
  @Override
  protected String url(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.getUri().toString();
  }

  @Override
  protected @Nullable String flavor(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  protected String method(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.method().name();
  }

  @Override
  protected List<String> requestHeader(ExecutionAttributes request, String name) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    List<String> value = httpRequest.headers().get(name);
    return value == null ? emptyList() : value;
  }

  @Override
  protected @Nullable Long requestContentLength(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }

  @Override
  protected Integer statusCode(ExecutionAttributes request, SdkHttpResponse response) {
    return response.statusCode();
  }

  @Override
  protected @Nullable Long responseContentLength(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      ExecutionAttributes request, @Nullable SdkHttpResponse response) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      ExecutionAttributes request, SdkHttpResponse response, String name) {
    List<String> value = response.headers().get(name);
    return value == null ? emptyList() : value;
  }
}
