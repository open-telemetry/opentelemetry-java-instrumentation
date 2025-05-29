/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

final class GoogleHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  @Nullable
  public String getHttpRequestMethod(HttpRequest httpRequest) {
    return httpRequest.getRequestMethod();
  }

  @Override
  public String getUrlFull(HttpRequest httpRequest) {
    return httpRequest.getUrl().build();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest httpRequest, String name) {
    return httpRequest.getHeaders().getHeaderStringValues(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest httpRequest, HttpResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest httpRequest, HttpResponse httpResponse, String name) {
    return httpResponse.getHeaders().getHeaderStringValues(name);
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequest request) {
    return request.getUrl().getHost();
  }

  @Override
  public Integer getServerPort(HttpRequest request) {
    return request.getUrl().getPort();
  }
}
