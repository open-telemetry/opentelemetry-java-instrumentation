/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

final class ReactorNettyHttpClientAttributesGetter
    implements HttpClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

  @Override
  public String getUrlFull(HttpClientRequest request) {
    return request.resourceUrl();
  }

  @Override
  public String getHttpRequestMethod(HttpClientRequest request) {
    return request.method().name();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpClientRequest request, String name) {
    return request.requestHeaders().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpClientRequest request, HttpClientResponse response, @Nullable Throwable error) {
    return response.status().code();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpClientRequest request, HttpClientResponse response, String name) {
    return response.responseHeaders().getAll(name);
  }
}
