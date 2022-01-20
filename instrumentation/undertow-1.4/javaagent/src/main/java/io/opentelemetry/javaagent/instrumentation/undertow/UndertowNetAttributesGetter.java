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
  @Nullable
  public InetSocketAddress getAddress(HttpServerExchange exchange) {
    return exchange.getConnection().getPeerAddress(InetSocketAddress.class);
  }

  @Override
  public String transport(HttpServerExchange exchange) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }
}
