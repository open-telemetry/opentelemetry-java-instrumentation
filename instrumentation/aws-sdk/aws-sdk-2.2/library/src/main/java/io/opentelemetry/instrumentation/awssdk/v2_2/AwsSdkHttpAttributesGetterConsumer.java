/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.http.SdkHttpRequest;

import javax.annotation.Nullable;
import java.util.List;

import static java.util.Collections.emptyList;

class AwsSdkHttpAttributesGetterConsumer
    implements HttpClientAttributesGetter<ExecutionAttributes, software.amazon.awssdk.core.interceptor.Context.AfterExecution> {

  @Override
  public String getUrlFull(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.getUri().toString();
  }

  @Override
  public String getHttpRequestMethod(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.method().name();
  }

  @Override
  public List<String> getHttpRequestHeader(ExecutionAttributes request, String name) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    List<String> value = httpRequest.headers().get(name);
    return value == null ? emptyList() : value;
  }

  @Override
  public Integer getHttpResponseStatusCode(
      ExecutionAttributes request, software.amazon.awssdk.core.interceptor.Context.AfterExecution response, @Nullable Throwable error) {
    return response.httpResponse().statusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      ExecutionAttributes request, software.amazon.awssdk.core.interceptor.Context.AfterExecution response, String name) {
    List<String> value = response.httpResponse().headers().get(name);
    return value == null ? emptyList() : value;
  }

  @Override
  @Nullable
  public String getServerAddress(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.host();
  }

  @Override
  public Integer getServerPort(ExecutionAttributes request) {
    SdkHttpRequest httpRequest =
        request.getAttribute(TracingExecutionInterceptor.SDK_HTTP_REQUEST_ATTRIBUTE);
    return httpRequest.port();
  }
}
