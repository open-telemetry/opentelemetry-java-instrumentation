/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
enum JavaHttpClientAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse<?>> {
  INSTANCE;

  @Override
  public String getMethod(HttpRequest httpRequest) {
    return httpRequest.method();
  }

  @Override
  public String getUrl(HttpRequest httpRequest) {
    return httpRequest.uri().toString();
  }

  @Override
  public List<String> getRequestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().allValues(name);
  }

  @Override
  public Integer getStatusCode(
      HttpRequest httpRequest, HttpResponse<?> httpResponse, @Nullable Throwable error) {
    return httpResponse.statusCode();
  }

  @Override
  public List<String> getResponseHeader(
      HttpRequest httpRequest, HttpResponse<?> httpResponse, String name) {
    return httpResponse.headers().allValues(name);
  }
}
