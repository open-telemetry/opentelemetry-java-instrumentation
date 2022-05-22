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
  public String method(HttpServerExchange exchange) {
    return exchange.getRequestMethod().toString();
  }

  @Override
  public List<String> requestHeader(HttpServerExchange exchange, String name) {
    HeaderValues values = exchange.getRequestHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  @Nullable
  public Long requestContentLength(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    long requestContentLength = exchange.getRequestContentLength();
    return requestContentLength != -1 ? requestContentLength : null;
  }

  @Override
  @Nullable
  public Long requestContentLengthUncompressed(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return null;
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
  public Integer statusCode(HttpServerExchange exchange, HttpServerExchange unused) {
    return exchange.getStatusCode();
  }

  @Override
  @Nullable
  public Long responseContentLength(HttpServerExchange exchange, HttpServerExchange unused) {
    long responseContentLength = exchange.getResponseContentLength();
    return responseContentLength != -1 ? responseContentLength : null;
  }

  @Override
  @Nullable
  public Long responseContentLengthUncompressed(
      HttpServerExchange exchange, HttpServerExchange unused) {
    return null;
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
}
