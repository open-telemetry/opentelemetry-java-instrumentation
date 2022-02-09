/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientRequest.headersToList;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpResponse;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ApacheHttpClientRequest, HttpResponse> {

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
  @Nullable
  public Long requestContentLength(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  public Integer statusCode(ApacheHttpClientRequest request, HttpResponse response) {
    return response.getStatusLine().getStatusCode();
  }

  @Override
  @Nullable
  public String flavor(ApacheHttpClientRequest request, @Nullable HttpResponse response) {
    return request.getFlavor();
  }

  @Override
  @Nullable
  public Long responseContentLength(ApacheHttpClientRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      ApacheHttpClientRequest request, HttpResponse response) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      ApacheHttpClientRequest request, HttpResponse response, String name) {
    return headersToList(response.getHeaders(name));
  }
}
