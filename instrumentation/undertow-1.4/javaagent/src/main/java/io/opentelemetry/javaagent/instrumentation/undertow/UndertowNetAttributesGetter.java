/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.undertow.server.HttpServerExchange;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

public class UndertowNetAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<HttpServerExchange> {

  @Override
  public String getTransport(HttpServerExchange exchange) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
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
