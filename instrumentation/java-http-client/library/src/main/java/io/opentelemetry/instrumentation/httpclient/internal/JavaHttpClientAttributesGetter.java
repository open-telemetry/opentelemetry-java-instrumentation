/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpclient.internal;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import javax.annotation.Nullable;

enum JavaHttpClientAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse<?>> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(HttpRequest httpRequest) {
    return httpRequest.method();
  }

  @Override
  public String getUrlFull(HttpRequest httpRequest) {
    return httpRequest.uri().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.headers().allValues(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest httpRequest, HttpResponse<?> httpResponse, @Nullable Throwable error) {
    return httpResponse.statusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest httpRequest, HttpResponse<?> httpResponse, String name) {
    return httpResponse.headers().allValues(name);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(HttpRequest request, @Nullable HttpResponse<?> response) {
    return "http";
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(HttpRequest request, @Nullable HttpResponse<?> response) {
    HttpClient.Version version;
    if (response != null) {
      version = response.version();
    } else {
      version = request.version().orElse(null);
    }
    if (version == null) {
      return null;
    }
    switch (version) {
      case HTTP_1_1:
        return "1.1";
      case HTTP_2:
        return "2";
    }
    return null;
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequest request) {
    return request.uri().getHost();
  }

  @Override
  public Integer getServerPort(HttpRequest request) {
    return request.uri().getPort();
  }
}
