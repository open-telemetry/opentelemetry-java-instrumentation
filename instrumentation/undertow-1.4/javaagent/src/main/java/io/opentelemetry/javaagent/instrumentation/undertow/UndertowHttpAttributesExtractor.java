/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.instrumenter.http.HttpServerAttributesExtractor;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HeaderValues;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

public class UndertowHttpAttributesExtractor
    extends HttpServerAttributesExtractor<HttpServerExchange, HttpServerExchange> {

  @Override
  protected String method(HttpServerExchange exchange) {
    return exchange.getRequestMethod().toString();
  }

  @Override
  protected List<String> requestHeader(HttpServerExchange exchange, String name) {
    HeaderValues values = exchange.getRequestHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  @Nullable
  protected Long requestContentLength(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    long requestContentLength = exchange.getRequestContentLength();
    return requestContentLength != -1 ? requestContentLength : null;
  }

  @Override
  @Nullable
  protected Long requestContentLengthUncompressed(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return null;
  }

  @Override
  protected String flavor(HttpServerExchange exchange) {
    String flavor = exchange.getProtocol().toString();
    // remove HTTP/ prefix to comply with semantic conventions
    if (flavor.startsWith("HTTP/")) {
      flavor = flavor.substring("HTTP/".length());
    }
    return flavor;
  }

  @Override
  protected Integer statusCode(HttpServerExchange exchange, HttpServerExchange unused) {
    return exchange.getStatusCode();
  }

  @Override
  @Nullable
  protected Long responseContentLength(
      HttpServerExchange exchange, HttpServerExchange unused) {
    long responseContentLength = exchange.getResponseContentLength();
    return responseContentLength != -1 ? responseContentLength : null;
  }

  @Override
  @Nullable
  protected Long responseContentLengthUncompressed(
      HttpServerExchange exchange, HttpServerExchange unused) {
    return null;
  }

  @Override
  protected List<String> responseHeader(
      HttpServerExchange exchange, HttpServerExchange unused, String name) {
    HeaderValues values = exchange.getResponseHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  @Nullable
  protected String target(HttpServerExchange exchange) {
    String requestPath = exchange.getRequestPath();
    String queryString = exchange.getQueryString();
    if (requestPath != null && queryString != null && !queryString.isEmpty()) {
      return requestPath + "?" + queryString;
    }
    return requestPath;
  }

  @Override
  @Nullable
  protected String scheme(HttpServerExchange exchange) {
    return exchange.getRequestScheme();
  }

  @Override
  @Nullable
  protected String route(HttpServerExchange exchange) {
    return null;
  }

  @Override
  @Nullable
  protected String serverName(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return null;
  }
}
