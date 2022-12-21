/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ApacheHttpClientRequest, ApacheHttpClientResponse> {

  @Override
  public String method(ApacheHttpClientRequest request) {
    return request.getMethod();
  }

  @Override
  public String url(ApacheHttpClientRequest request) {
    return request.getUrl();
  }

  @Override
  public List<String> requestHeader(ApacheHttpClientRequest request, String name) {
    return request.getHeader(name);
  }

  @Override
  public Integer statusCode(
      ApacheHttpClientRequest request,
      ApacheHttpClientResponse response,
      @Nullable Throwable error) {
    return response.getStatusCode();
  }

  @Override
  @Nullable
  public String flavor(
      ApacheHttpClientRequest request, @Nullable ApacheHttpClientResponse response) {
    String flavor = request.getFlavor();
    if (flavor == null && response != null) {
      flavor = response.getFlavor();
    }
    return flavor;
  }

  @Override
  public List<String> responseHeader(
      ApacheHttpClientRequest request, ApacheHttpClientResponse response, String name) {
    return response.getHeader(name);
  }
}
