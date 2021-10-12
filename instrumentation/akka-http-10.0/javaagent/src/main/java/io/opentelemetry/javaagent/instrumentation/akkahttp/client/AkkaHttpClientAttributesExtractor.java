/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

class AkkaHttpClientAttributesExtractor
    extends HttpClientAttributesExtractor<HttpRequest, HttpResponse> {
  @Override
  protected String url(HttpRequest httpRequest) {
    return httpRequest.uri().toString();
  }

  @Override
  protected String flavor(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return AkkaHttpUtil.flavor(httpRequest);
  }

  @Override
  protected String method(HttpRequest httpRequest) {
    return httpRequest.method().value();
  }

  @Override
  protected List<String> requestHeader(HttpRequest httpRequest, String name) {
    return AkkaHttpUtil.requestHeader(httpRequest, name);
  }

  @Override
  protected @Nullable Long requestContentLength(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpRequest httpRequest, HttpResponse httpResponse) {
    return httpResponse.status().intValue();
  }

  @Override
  protected @Nullable Long responseContentLength(
      HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      HttpRequest httpRequest, HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpRequest httpRequest, HttpResponse httpResponse, String name) {
    return AkkaHttpUtil.responseHeader(httpResponse, name);
  }
}
