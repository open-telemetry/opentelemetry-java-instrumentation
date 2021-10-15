/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;
import scala.Option;

class AkkaHttpServerAttributesExtractor
    extends HttpServerAttributesExtractor<HttpRequest, HttpResponse> {

  @Override
  protected String method(HttpRequest request) {
    return request.method().value();
  }

  @Override
  protected List<String> requestHeader(HttpRequest request, String name) {
    return AkkaHttpUtil.requestHeader(request, name);
  }

  @Override
  protected @Nullable Long requestContentLength(
      HttpRequest request, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
      HttpRequest request, @Nullable HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected Integer statusCode(HttpRequest request, HttpResponse httpResponse) {
    return httpResponse.status().intValue();
  }

  @Override
  protected @Nullable Long responseContentLength(HttpRequest request, HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
      HttpRequest request, HttpResponse httpResponse) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpRequest request, HttpResponse httpResponse, String name) {
    return AkkaHttpUtil.responseHeader(httpResponse, name);
  }

  @Override
  protected String flavor(HttpRequest request) {
    return AkkaHttpUtil.flavor(request);
  }

  @Override
  protected String target(HttpRequest request) {
    String target = request.uri().path().toString();
    Option<String> queryString = request.uri().rawQueryString();
    if (queryString.isDefined()) {
      target += "?" + queryString.get();
    }
    return target;
  }

  @Override
  protected @Nullable String route(HttpRequest request) {
    return null;
  }

  @Override
  protected String scheme(HttpRequest request) {
    return request.uri().scheme();
  }

  @Override
  protected @Nullable String serverName(HttpRequest request, @Nullable HttpResponse httpResponse) {
    return null;
  }
}
