/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.undertow.server.HttpServerExchange;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

public class UndertowNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<HttpServerExchange, HttpServerExchange> {

  @Override
  public @Nullable InetSocketAddress getAddress(HttpServerExchange exchange) {
    return exchange.getConnection().getPeerAddress(InetSocketAddress.class);
  }

  @Override
  public String transport(HttpServerExchange exchange) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }
}
