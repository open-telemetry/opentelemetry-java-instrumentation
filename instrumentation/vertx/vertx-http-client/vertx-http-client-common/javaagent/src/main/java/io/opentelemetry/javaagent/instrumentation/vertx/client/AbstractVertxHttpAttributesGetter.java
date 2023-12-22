/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.util.List;
import javax.annotation.Nullable;

public abstract class AbstractVertxHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

  @Override
  public List<String> getHttpRequestHeader(HttpClientRequest request, String name) {
    return request.headers().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpClientRequest request, HttpClientResponse response, @Nullable Throwable error) {
    return response.statusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpClientRequest request, HttpClientResponse response, String name) {
    return response.headers().getAll(name);
  }
}
