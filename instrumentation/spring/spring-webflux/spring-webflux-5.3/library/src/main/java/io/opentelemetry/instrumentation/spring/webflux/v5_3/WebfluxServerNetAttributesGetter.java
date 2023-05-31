/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.springframework.web.server.ServerWebExchange;

final class WebfluxServerNetAttributesGetter
    implements NetServerAttributesGetter<ServerWebExchange, ServerWebExchange> {

  @Nullable
  @Override
  public String getHostName(ServerWebExchange request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getHostPort(ServerWebExchange request) {
    int port = request.getRequest().getURI().getPort();
    return port == -1 ? null : port;
  }

  @Nullable
  @Override
  public InetSocketAddress getPeerSocketAddress(ServerWebExchange request) {
    return request.getRequest().getRemoteAddress();
  }

  @Nullable
  @Override
  public InetSocketAddress getHostSocketAddress(ServerWebExchange request) {
    return request.getRequest().getLocalAddress();
  }
}
