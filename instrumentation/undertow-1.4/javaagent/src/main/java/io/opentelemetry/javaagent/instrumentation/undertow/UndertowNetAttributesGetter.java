/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.undertow.server.HttpServerExchange;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

public class UndertowNetAttributesGetter
    implements NetServerAttributesGetter<HttpServerExchange, HttpServerExchange> {

  @Nullable
  @Override
  public String getNetworkProtocolName(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    String protocol = exchange.getProtocol().toString();
    if (protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    String protocol = exchange.getProtocol().toString();
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(HttpServerExchange exchange) {
    return exchange.getHostName();
  }

  @Nullable
  @Override
  public Integer getServerPort(HttpServerExchange exchange) {
    return exchange.getHostPort();
  }

  @Override
  @Nullable
  public InetSocketAddress getClientInetSocketAddress(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return exchange.getConnection().getPeerAddress(InetSocketAddress.class);
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      HttpServerExchange exchange, @Nullable HttpServerExchange unused) {
    return exchange.getConnection().getLocalAddress(InetSocketAddress.class);
  }
}
