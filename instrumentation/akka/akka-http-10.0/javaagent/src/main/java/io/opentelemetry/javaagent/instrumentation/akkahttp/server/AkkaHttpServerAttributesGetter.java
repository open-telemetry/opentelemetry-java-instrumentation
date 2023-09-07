/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.http.scaladsl.model.Uri;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import java.util.List;
import javax.annotation.Nullable;
import scala.Option;

class AkkaHttpServerAttributesGetter
    implements HttpServerAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getHttpRequestMethod(HttpRequest request) {
    return request.method().value();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest request, String name) {
    return AkkaHttpUtil.requestHeader(request, name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest request, HttpResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.status().intValue();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest request, HttpResponse httpResponse, String name) {
    return AkkaHttpUtil.responseHeader(httpResponse, name);
  }

  @Override
  public String getUrlScheme(HttpRequest request) {
    return request.uri().scheme();
  }

  @Override
  public String getUrlPath(HttpRequest request) {
    return request.uri().path().toString();
  }

  @Nullable
  @Override
  public String getUrlQuery(HttpRequest request) {
    Option<String> queryString = request.uri().rawQueryString();
    return queryString.isDefined() ? queryString.get() : null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(HttpRequest request, @Nullable HttpResponse httpResponse) {
    return AkkaHttpUtil.protocolName(request);
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpRequest request, @Nullable HttpResponse httpResponse) {
    return AkkaHttpUtil.protocolVersion(request);
  }

  @Nullable
  @Override
  public String getServerAddress(HttpRequest request) {
    Uri.Host host = request.uri().authority().host();
    return host.isEmpty() ? null : host.address();
  }

  @Override
  public Integer getServerPort(HttpRequest request) {
    return request.uri().authority().port();
  }
}
