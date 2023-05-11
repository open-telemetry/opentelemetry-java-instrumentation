/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;

final class JoddHttpHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getMethod(HttpRequest request) {
    return request.method();
  }

  @Override
  public String getUrl(HttpRequest request) {
    return request.url();
  }

  @Override
  public List<String> getRequestHeader(HttpRequest request, String name) {
    return request.headers(name);
  }

  @Override
  public Integer getStatusCode(
      HttpRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.statusCode();
  }

  @Override
  public List<String> getResponseHeader(HttpRequest request, HttpResponse response, String name) {
    return response.headers(name);
  }
}
