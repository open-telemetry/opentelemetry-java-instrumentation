/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ApacheHttpRequest, ApacheHttpResponse> {

  @Override
  public String getMethod(ApacheHttpRequest request) {
    return request.getMethod();
  }

  @Override
  public String getUrl(ApacheHttpRequest request) {
    return request.getUrl();
  }

  @Override
  public List<String> getRequestHeader(ApacheHttpRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public Integer getStatusCode(ApacheHttpRequest request, ApacheHttpResponse response, @Nullable Throwable error) {
    return response.getStatusCode();
  }

  @Override
  @Nullable
  public String getFlavor(ApacheHttpRequest request, @Nullable ApacheHttpResponse response) {
    String flavor = request.getFlavor();
    if (flavor == null && response != null) {
      flavor = response.getFlavor();
    }
    return flavor;
  }

  @Override
  public List<String> getResponseHeader(ApacheHttpRequest request, ApacheHttpResponse response, String name) {
    return response.getHeader(name);
  }
}
