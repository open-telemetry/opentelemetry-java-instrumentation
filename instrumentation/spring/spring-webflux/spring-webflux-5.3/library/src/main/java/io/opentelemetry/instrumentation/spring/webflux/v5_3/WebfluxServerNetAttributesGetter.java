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
  public String getServerAddress(ServerWebExchange request) {
    return request.getRequest().getURI().getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(ServerWebExchange request) {
    int port = request.getRequest().getURI().getPort();
    return port == -1 ? null : port;
  }

  @Nullable
  @Override
  public InetSocketAddress getClientInetSocketAddress(
      ServerWebExchange request, @Nullable ServerWebExchange response) {
    return request.getRequest().getRemoteAddress();
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      ServerWebExchange request, @Nullable ServerWebExchange response) {
    return request.getRequest().getLocalAddress();
  }
}
