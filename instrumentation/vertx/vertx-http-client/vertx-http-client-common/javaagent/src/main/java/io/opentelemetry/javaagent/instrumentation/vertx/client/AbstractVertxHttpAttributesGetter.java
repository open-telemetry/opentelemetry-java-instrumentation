/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import java.util.List;
import javax.annotation.Nullable;

public abstract class AbstractVertxHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

  @Nullable
  @Override
  public String flavor(HttpClientRequest request, @Nullable HttpClientResponse response) {
    return null;
  }

  @Override
  public List<String> requestHeader(HttpClientRequest request, String name) {
    return request.headers().getAll(name);
  }

  @Override
  public Integer statusCode(HttpClientRequest request, HttpClientResponse response) {
    return response.statusCode();
  }

  @Override
  public List<String> responseHeader(
      HttpClientRequest request, HttpClientResponse response, String name) {
    return response.headers().getAll(name);
  }
}
