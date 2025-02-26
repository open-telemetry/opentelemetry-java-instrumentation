/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.activejhttp;

import io.activej.http.HttpHeader;
import io.activej.http.HttpHeaderValue;
import io.activej.http.HttpHeaders;
import io.activej.http.HttpRequest;
import io.activej.http.HttpResponse;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class ActivejHttpServerHttpAttributesGetter
    implements HttpServerAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getHttpRequestMethod(HttpRequest request) {
    return request.getMethod().name();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest request, String name) {
    HttpHeader httpHeader = HttpHeaders.of(name);
    List<String> values = new ArrayList<>();
    for (Map.Entry<HttpHeader, HttpHeaderValue> entry : request.getHeaders()) {
      if (httpHeader.equals(entry.getKey())) {
        values.add(entry.getValue().toString());
      }
    }

    return values;
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest request, HttpResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.getCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest request, HttpResponse httpResponse, String name) {
    HttpHeader httpHeader = HttpHeaders.of(name);
    List<String> values = new ArrayList<>();
    for (Map.Entry<HttpHeader, HttpHeaderValue> entry : httpResponse.getHeaders()) {
      if (httpHeader.equals(entry.getKey())) {
        values.add(entry.getValue().toString());
      }
    }

    return values;
  }

  @Override
  public String getUrlScheme(HttpRequest request) {
    return request.getProtocol().lowercase();
  }

  @Override
  public String getUrlPath(HttpRequest request) {
    return request.getPath();
  }

  @Override
  public String getUrlQuery(HttpRequest request) {
    return request.getQuery();
  }

  @Override
  public String getNetworkProtocolName(HttpRequest request, @Nullable HttpResponse httpResponse) {
    return switch (request.getVersion()) {
      case HTTP_0_9, HTTP_1_0, HTTP_1_1, HTTP_2_0 -> "http";
      default -> null;
    };
  }

  @Override
  public String getNetworkProtocolVersion(
      HttpRequest request, @Nullable HttpResponse httpResponse) {
    return switch (request.getVersion()) {
      case HTTP_0_9 -> "0.9";
      case HTTP_1_0 -> "1.0";
      case HTTP_1_1 -> "1.1";
      case HTTP_2_0 -> "2";
      default -> null;
    };
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(HttpRequest request, @Nullable HttpResponse httpResponse) {
    InetAddress remoteAddress = request.getConnection().getRemoteAddress();
    return remoteAddress != null ? remoteAddress.getHostAddress() : null;
  }
}
