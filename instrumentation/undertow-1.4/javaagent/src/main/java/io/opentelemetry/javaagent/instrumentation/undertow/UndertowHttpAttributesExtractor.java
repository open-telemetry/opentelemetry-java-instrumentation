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
import org.checkerframework.checker.nullness.qual.Nullable;

public class UndertowHttpAttributesExtractor
    extends HttpServerAttributesExtractor<HttpServerExchange, HttpServerExchange> {

  @Override
  protected String method(HttpServerExchange exchange) {
    return exchange.getRequestMethod().toString();
  }

  @Override
  protected @Nullable String userAgent(HttpServerExchange exchange) {
    return exchange.getRequestHeaders().getFirst("User-Agent");
  }

  @Override
  protected List<String> requestHeader(HttpServerExchange exchange, String name) {
    HeaderValues values = exchange.getRequestHeaders().get(name);
    return values == null ? Collections.emptyList() : values;
  }

  @Override
  protected @Nullable Long requestContentLength(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    long requestContentLength = exchange.getRequestContentLength();
    return requestContentLength != -1 ? requestContentLength : null;
  }

  @Override
  protected @Nullable Long requestContentLengthUncompressed(
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
  protected @Nullable Long responseContentLength(
      HttpServerExchange exchange, HttpServerExchange unused) {
    long responseContentLength = exchange.getResponseContentLength();
    return responseContentLength != -1 ? responseContentLength : null;
  }

  @Override
  protected @Nullable Long responseContentLengthUncompressed(
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
  protected @Nullable String target(HttpServerExchange exchange) {
    String requestPath = exchange.getRequestPath();
    String queryString = exchange.getQueryString();
    if (requestPath != null && queryString != null && !queryString.isEmpty()) {
      return requestPath + "?" + queryString;
    }
    return requestPath;
  }

  @Override
  protected @Nullable String host(HttpServerExchange exchange) {
    return exchange.getHostAndPort();
  }

  @Override
  protected @Nullable String scheme(HttpServerExchange exchange) {
    return exchange.getRequestScheme();
  }

  @Override
  protected @Nullable String route(HttpServerExchange exchange) {
    return null;
  }

  @Override
  protected @Nullable String serverName(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return null;
  }
}
