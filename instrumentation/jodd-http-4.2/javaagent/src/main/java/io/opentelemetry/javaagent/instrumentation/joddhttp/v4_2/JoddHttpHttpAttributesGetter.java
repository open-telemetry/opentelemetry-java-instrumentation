/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;

final class JoddHttpHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getHttpRequestMethod(HttpRequest request) {
    return request.method();
  }

  @Override
  public String getUrlFull(HttpRequest request) {
    return request.url();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest request, String name) {
    return request.headers(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.statusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest request, HttpResponse response, String name) {
    return response.headers(name);
  }

  @Override
  public String getNetworkProtocolName(HttpRequest request, @Nullable HttpResponse response) {
    return "http";
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(HttpRequest request, @Nullable HttpResponse response) {
    String httpVersion = request.httpVersion();
    if (httpVersion == null && response != null) {
      httpVersion = response.httpVersion();
    }
    if (httpVersion != null) {
      if (httpVersion.contains("/")) {
        httpVersion = httpVersion.substring(httpVersion.lastIndexOf("/") + 1);
      }
    }
    return httpVersion;
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequest request) {
    return request.host();
  }

  @Override
  public Integer getServerPort(HttpRequest request) {
    return request.port();
  }
}
