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
  public String getHttpRequestMethod(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  @Nullable
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
}
