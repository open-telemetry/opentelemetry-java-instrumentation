/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientRequest.headersToList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpClientAttributeGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

final class ApacheHttpClientHttpAttributeGetter
    implements HttpClientAttributeGetter<ApacheHttpClientRequest, HttpResponse> {

  @Override
  public String getHttpRequestMethod(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  public String getUrlFull(ApacheHttpClientRequest request) {
    return request.getUrl();
  }

  @Override
  public List<String> getHttpRequestHeader(ApacheHttpClientRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      ApacheHttpClientRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.getStatusLine().getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      ApacheHttpClientRequest request, HttpResponse response, String name) {
    return headersToList(response.getHeaders(name));
  }

  @Override
  public String getNetworkProtocolName(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getProtocolName();
  }

  @Override
  public String getNetworkProtocolVersion(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getProtocolVersion();
  }

  @Override
  @Nullable
  public String getServerAddress(ApacheHttpClientRequest request) {
    return request.getServerAddress();
  }

  @Override
  public Integer getServerPort(ApacheHttpClientRequest request) {
    return request.getServerPort();
  }
}
