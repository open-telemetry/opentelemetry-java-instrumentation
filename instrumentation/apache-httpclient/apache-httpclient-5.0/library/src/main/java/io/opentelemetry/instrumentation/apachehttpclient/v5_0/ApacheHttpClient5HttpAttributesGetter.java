/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributesGetter;
import jakarta.annotation.Nullable;
import java.util.List;
import org.apache.hc.core5.http.HttpResponse;

enum ApacheHttpClient5HttpAttributesGetter
    implements HttpClientAttributesGetter<ApacheHttpClient5Request, HttpResponse> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(ApacheHttpClient5Request request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  public String getUrlFull(ApacheHttpClient5Request request) {
    return request.getUrl();
  }

  @Override
  public List<String> getHttpRequestHeader(ApacheHttpClient5Request request, String name) {
    return request.getHeader(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      ApacheHttpClient5Request request, HttpResponse response, @Nullable Throwable error) {
    return response.getCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      ApacheHttpClient5Request request, HttpResponse response, String name) {
    return ApacheHttpClient5Request.headersToList(response.getHeaders(name));
  }

  @Override
  public String getNetworkProtocolName(
      ApacheHttpClient5Request request, @Nullable HttpResponse response) {
    return request.getProtocolName();
  }

  @Override
  public String getNetworkProtocolVersion(
      ApacheHttpClient5Request request, @Nullable HttpResponse response) {
    return request.getProtocolVersion();
  }

  @Override
  @Nullable
  public String getServerAddress(ApacheHttpClient5Request request) {
    return request.getServerAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(ApacheHttpClient5Request request) {
    return request.getServerPort();
  }
}
