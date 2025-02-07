/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.httpserver.internal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsExchange;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum JdkHttpServerAttributesGetter
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
    String fullPath = exchange.getRequestURI().toString();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? fullPath : fullPath.substring(0, separatorPos);
  }

  @Nullable
  @Override
  public String getUrlQuery(HttpExchange exchange) {
    String fullPath = exchange.getRequestURI().toString();
    int separatorPos = fullPath.indexOf('?');
    return separatorPos == -1 ? null : fullPath.substring(separatorPos + 1);
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
    if (status > 1) {
      return status;
    }
    return null;
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
    return exchange instanceof HttpsExchange ? "https" : "http";
  }

  @Override
  public String getNetworkProtocolVersion(HttpExchange exchange, @Nullable HttpExchange res) {

    return "1.1";
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
