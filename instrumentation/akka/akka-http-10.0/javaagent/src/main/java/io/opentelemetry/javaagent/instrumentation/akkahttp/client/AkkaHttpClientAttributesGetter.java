/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import java.util.List;
import javax.annotation.Nullable;

class AkkaHttpClientAttributesGetter
    implements HttpClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String url(HttpRequest httpRequest) {
    return httpRequest.uri().toString();
  }

  @Override
  public String flavor(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return AkkaHttpUtil.flavor(httpRequest);
  }

  @Override
  public String method(HttpRequest httpRequest) {
    return httpRequest.method().value();
  }

  @Override
  public List<String> requestHeader(HttpRequest httpRequest, String name) {
    return AkkaHttpUtil.requestHeader(httpRequest, name);
  }

  @Override
  @Nullable
  public Long requestContentLength(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  public Integer statusCode(HttpRequest httpRequest, HttpResponse httpResponse) {
    return httpResponse.status().intValue();
  }

  @Override
  @Nullable
  public Long responseContentLength(HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  public List<String> responseHeader(
      HttpRequest httpRequest, HttpResponse httpResponse, String name) {
    return AkkaHttpUtil.responseHeader(httpResponse, name);
  }
}
