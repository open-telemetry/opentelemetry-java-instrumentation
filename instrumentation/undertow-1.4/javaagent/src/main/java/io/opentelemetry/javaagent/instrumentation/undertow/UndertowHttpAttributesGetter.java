/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesGetter;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class UndertowHttpAttributesGetter
    implements HttpServerAttributesGetter<HttpServerExchange, HttpServerExchange> {

  @Override
  public String getMethod(HttpServerExchange exchange) {
    return exchange.getRequestMethod().toString();
  }

  @Override
  public List<String> getRequestHeader(HttpServerExchange exchange, String name) {
    HeaderValues values = exchange.getRequestHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  public String getFlavor(HttpServerExchange exchange) {
    String flavor = exchange.getProtocol().toString();
    // remove HTTP/ prefix to comply with semantic conventions
    if (flavor.startsWith("HTTP/")) {
      flavor = flavor.substring("HTTP/".length());
    }
    return flavor;
  }

  @Override
  public Integer getStatusCode(
      HttpServerExchange exchange, HttpServerExchange unused, @Nullable Throwable error) {
    return exchange.getStatusCode();
  }

  @Override
  public List<String> getResponseHeader(
      HttpServerExchange exchange, HttpServerExchange unused, String name) {
    HeaderValues values = exchange.getResponseHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  @Nullable
  public String getTarget(HttpServerExchange exchange) {
    String requestPath = exchange.getRequestPath();
    String queryString = exchange.getQueryString();
    if (requestPath != null && queryString != null && !queryString.isEmpty()) {
      return requestPath + "?" + queryString;
    }
    return requestPath;
  }

  @Override
  @Nullable
  public String getScheme(HttpServerExchange exchange) {
    return exchange.getRequestScheme();
  }
}
