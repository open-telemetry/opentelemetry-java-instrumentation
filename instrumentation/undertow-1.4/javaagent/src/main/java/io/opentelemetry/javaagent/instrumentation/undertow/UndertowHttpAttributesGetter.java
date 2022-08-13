/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import io.undertow.util.HttpString;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.json.JSONObject;

public class UndertowHttpAttributesGetter
    implements HttpServerAttributesGetter<HttpServerExchange, HttpServerExchange> {

  @Override
  public String method(HttpServerExchange exchange) {
    return exchange.getRequestMethod().toString();
  }

  @Override
  public List<String> requestHeader(HttpServerExchange exchange, String name) {
    HeaderValues values = exchange.getRequestHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  private static String firstListValue(List<String> values) {
    return values.isEmpty() ? "" : values.get(0);
  }

  @Override
  @Nullable
  public String requestHeaders(HttpServerExchange exchange, HttpServerExchange unused) {
    return toJsonString(
        exchange.getRequestHeaders().getHeaderNames().stream()
            .map(HttpString::toString)
            .collect(
                Collectors.toMap(
                    Function.identity(), (h) -> firstListValue(requestHeader(exchange, h)))));
  }

  @Override
  @Nullable
  public String responseHeaders(HttpServerExchange unused, HttpServerExchange exchange) {
    return toJsonString(
        exchange.getResponseHeaders().getHeaderNames().stream()
            .map(HttpString::toString)
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    (h) -> firstListValue(responseHeader(exchange, exchange, h)))));
  }

  @Override
  public String flavor(HttpServerExchange exchange) {
    String flavor = exchange.getProtocol().toString();
    // remove HTTP/ prefix to comply with semantic conventions
    if (flavor.startsWith("HTTP/")) {
      flavor = flavor.substring("HTTP/".length());
    }
    return flavor;
  }

  @Override
  public Integer statusCode(
      HttpServerExchange exchange, HttpServerExchange unused, @Nullable Throwable error) {
    return exchange.getStatusCode();
  }

  @Override
  public List<String> responseHeader(
      HttpServerExchange exchange, HttpServerExchange unused, String name) {
    HeaderValues values = exchange.getResponseHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  @Nullable
  public String target(HttpServerExchange exchange) {
    String requestPath = exchange.getRequestPath();
    String queryString = exchange.getQueryString();
    if (requestPath != null && queryString != null && !queryString.isEmpty()) {
      return requestPath + "?" + queryString;
    }
    return requestPath;
  }

  @Override
  @Nullable
  public String scheme(HttpServerExchange exchange) {
    return exchange.getRequestScheme();
  }

  @Override
  @Nullable
  public String route(HttpServerExchange exchange) {
    return null;
  }

  @Override
  @Nullable
  public String serverName(HttpServerExchange exchange) {
    return null;
  }

  @Nullable
  static String toJsonString(Map<String, String> m) {
    return new JSONObject(m).toString();
  }
}
