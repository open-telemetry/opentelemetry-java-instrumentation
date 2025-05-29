/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import io.opentelemetry.instrumentation.api.internal.HttpProtocolUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum JavaHttpServerAttributesGetter
    implements HttpServerAttributesGetter<HttpExchange, HttpExchange> {
  INSTANCE;

  @Override
  public String getHttpRequestMethod(HttpExchange exchange) {
    return exchange.getRequestMethod();
  }

  @Override
  public String getUrlScheme(HttpExchange exchange) {
    return exchange instanceof HttpsExchange ? "https" : "http";
  }

  @Override
  public String getUrlPath(HttpExchange exchange) {
    return exchange.getRequestURI().getPath();
  }

  @Nullable
  @Override
  public String getUrlQuery(HttpExchange exchange) {
    return exchange.getRequestURI().getQuery();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpExchange exchange, String name) {
    return exchange.getRequestHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Nullable
  @Override
  public Integer getHttpResponseStatusCode(
      HttpExchange exchange, @Nullable HttpExchange res, @Nullable Throwable error) {
    int status = exchange.getResponseCode();
    return status != -1 ? status : null;
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpExchange exchange, @Nullable HttpExchange res, String name) {
    return exchange.getResponseHeaders().getOrDefault(name, Collections.emptyList());
  }

  @Override
  public String getHttpRoute(HttpExchange exchange) {
    return exchange.getHttpContext().getPath();
  }

  @Override
  public String getNetworkProtocolName(HttpExchange exchange, @Nullable HttpExchange res) {
    return HttpProtocolUtil.getProtocol(exchange.getProtocol());
  }

  @Override
  public String getNetworkProtocolVersion(HttpExchange exchange, @Nullable HttpExchange res) {
    return HttpProtocolUtil.getVersion(exchange.getProtocol());
  }

  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      HttpExchange exchange, @Nullable HttpExchange res) {
    return exchange.getRemoteAddress();
  }

  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      HttpExchange exchange, @Nullable HttpExchange res) {
    return exchange.getLocalAddress();
  }
}
