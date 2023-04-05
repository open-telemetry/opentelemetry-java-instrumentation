/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpClient.Version;
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
  public String getFlavor(HttpRequest httpRequest, @Nullable HttpResponse<?> httpResponse) {
    if (httpResponse != null && httpResponse.version() == Version.HTTP_2) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
  }

  @Override
  public List<String> getResponseHeader(
      HttpRequest httpRequest, HttpResponse<?> httpResponse, String name) {
    return httpResponse.headers().allValues(name);
  }
}
