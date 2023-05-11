/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import io.undertow.server.HttpServerExchange;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

public class UndertowNetAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<HttpServerExchange> {

  @Nullable
  @Override
  public String getProtocolName(HttpServerExchange exchange) {
    String protocol = exchange.getProtocol().toString();
    if (protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(HttpServerExchange exchange) {
    String protocol = exchange.getProtocol().toString();
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getHostName(HttpServerExchange exchange) {
    return exchange.getHostName();
  }

  @Nullable
  @Override
  public Integer getHostPort(HttpServerExchange exchange) {
    return exchange.getHostPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(HttpServerExchange exchange) {
    return exchange.getConnection().getPeerAddress(InetSocketAddress.class);
  }

  @Nullable
  @Override
  protected InetSocketAddress getHostSocketAddress(HttpServerExchange exchange) {
    return exchange.getConnection().getLocalAddress(InetSocketAddress.class);
  }
}
