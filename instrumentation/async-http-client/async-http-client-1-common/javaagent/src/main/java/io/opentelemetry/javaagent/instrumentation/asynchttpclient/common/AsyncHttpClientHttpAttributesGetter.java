/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.asynchttpclient.common;

import static java.util.Collections.emptyList;

import com.ning.http.client.Request;
import com.ning.http.client.Response;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

final class AsyncHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<Request, Response> {

  private final AsyncHttpClientHelper helper;

  AsyncHttpClientHttpAttributesGetter(AsyncHttpClientHelper helper) {
    this.helper = helper;
  }

  @Override
  public String getHttpRequestMethod(Request request) {
    return request.getMethod();
  }

  @Override
  public String getUrlFull(Request request) {
    return helper.getUrlFull(request);
  }

  @Override
  public List<String> getHttpRequestHeader(Request request, String name) {
    return request.getHeaders().getOrDefault(name, emptyList());
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request request, Response response, @Nullable Throwable error) {
    return response.getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(Request request, Response response, String name) {
    return response.getHeaders().getOrDefault(name, emptyList());
  }

  @Override
  public String getServerAddress(Request request) {
    return helper.getServerAddress(request);
  }

  @Override
  public Integer getServerPort(Request request) {
    return helper.getServerPort(request);
  }
}
