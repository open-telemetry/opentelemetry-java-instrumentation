/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

class AwsSdkHttpAttributesGetter implements HttpClientAttributesGetter<Request<?>, Response<?>> {

  @Override
  public String getUrlFull(Request<?> request) {
    return request.getEndpoint().toString();
  }

  @Override
  public String getHttpRequestMethod(Request<?> request) {
    return request.getHttpMethod().name();
  }

  @Override
  public List<String> getHttpRequestHeader(Request<?> request, String name) {
    String value = request.getHeaders().get(name.equals("user-agent") ? "User-Agent" : name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request<?> request, Response<?> response, @Nullable Throwable error) {
    return response.getHttpResponse().getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(Request<?> request, Response<?> response, String name) {
    String value = response.getHttpResponse().getHeaders().get(name);
    return value == null ? emptyList() : singletonList(value);
  }
}
