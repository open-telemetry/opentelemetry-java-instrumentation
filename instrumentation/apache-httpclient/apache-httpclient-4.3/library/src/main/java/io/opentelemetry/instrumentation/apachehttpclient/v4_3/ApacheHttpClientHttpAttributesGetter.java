/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import static io.opentelemetry.instrumentation.apachehttpclient.v4_3.ApacheHttpClientRequest.headersToList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

enum ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ApacheHttpClientRequest, HttpResponse> {
  INSTANCE;

  @Override
  public String getMethod(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  @Nullable
  public String getUrl(ApacheHttpClientRequest request) {
    return request.getUrl();
  }

  @Override
  public List<String> getRequestHeader(ApacheHttpClientRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public Integer getStatusCode(
      ApacheHttpClientRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.getStatusLine().getStatusCode();
  }

  @Override
  @Nullable
  public String getFlavor(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getFlavor();
  }

  @Override
  public List<String> getResponseHeader(
      ApacheHttpClientRequest request, HttpResponse response, String name) {
    return headersToList(response.getHeaders(name));
  }
}
