/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.internal.HttpProtocolUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class UndertowHttpAttributesGetter
    implements HttpServerAttributesGetter<HttpServerExchange, HttpServerExchange> {

  @Override
  public String getHttpRequestMethod(HttpServerExchange exchange) {
    return exchange.getRequestMethod().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpServerExchange exchange, String name) {
    HeaderValues values = exchange.getRequestHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpServerExchange exchange, HttpServerExchange unused, @Nullable Throwable error) {
    return exchange.getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpServerExchange exchange, HttpServerExchange unused, String name) {
    HeaderValues values = exchange.getResponseHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  @Nullable
  public String getUrlScheme(HttpServerExchange exchange) {
    return exchange.getRequestScheme();
  }

  @Nullable
  @Override
  public String getUrlPath(HttpServerExchange exchange) {
    return exchange.getRequestPath();
  }

  @Nullable
  @Override
  public String getUrlQuery(HttpServerExchange exchange) {
    String queryString = exchange.getQueryString();
    // getQueryString returns empty string when query string is missing, we'll return null from
    // here instead to void adding empty query string attribute to the span
    return !"".equals(queryString) ? queryString : null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return HttpProtocolUtil.getProtocol(exchange.getProtocol().toString());
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return HttpProtocolUtil.getVersion(exchange.getProtocol().toString());
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return exchange.getConnection().getPeerAddress(InetSocketAddress.class);
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return exchange.getConnection().getLocalAddress(InetSocketAddress.class);
  }
}
