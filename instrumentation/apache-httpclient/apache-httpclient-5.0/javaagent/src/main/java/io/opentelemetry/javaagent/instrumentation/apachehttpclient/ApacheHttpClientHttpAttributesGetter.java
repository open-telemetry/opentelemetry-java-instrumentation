/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;

final class ApacheHttpClientHttpAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String method(HttpRequest request) {
    return HttpUtils.getMethod(request);
  }

  @Override
  public String url(HttpRequest request) {
    return HttpUtils.getUrl(request);
  }

  @Override
  public List<String> requestHeader(HttpRequest request, String name) {
    return HttpUtils.getHeader(request, name);
  }

  @Override
  @Nullable
  public Long requestContentLength(HttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      HttpRequest request, @Nullable HttpResponse response) {
    return null;
  }

  @Override
  public Integer statusCode(HttpRequest request, HttpResponse response) {
    return HttpUtils.getStatusCode(response);
  }

  @Override
  @Nullable
  public String flavor(HttpRequest request, @Nullable HttpResponse response) {
    return HttpUtils.getFlavor(request, response);
  }

  @Override
  @Nullable
  public Long responseContentLength(HttpRequest request, HttpResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(HttpRequest request, HttpResponse response) {
    return null;
  }

  @Override
  public List<String> responseHeader(HttpRequest request, HttpResponse response, String name) {
    return HttpUtils.getHeader(response, name);
  }
}
