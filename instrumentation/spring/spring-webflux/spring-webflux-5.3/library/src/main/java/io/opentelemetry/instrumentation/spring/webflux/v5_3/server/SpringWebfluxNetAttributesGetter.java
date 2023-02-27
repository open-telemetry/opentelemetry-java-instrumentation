/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.server;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.springframework.web.server.ServerWebExchange;

enum SpringWebfluxNetAttributesGetter implements NetServerAttributesGetter<ServerWebExchange> {
  INSTANCE;

  @Override
  public String getTransport(ServerWebExchange request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getHostName(ServerWebExchange request) {
    String host = request.getRequest().getURI().getHost();
    if (host.equals("127.0.0.1")) {
      return "localhost";
    }
    return host;
  }

  @Nullable
  @Override
  public Integer getHostPort(ServerWebExchange request) {
    int port = request.getRequest().getURI().getPort();
    return port == -1 ? null : port;
  }

  @Nullable
  @Override
  public String getSockFamily(ServerWebExchange request) {
    return null;
  }

  @Nullable
  @Override
  public String getSockPeerAddr(ServerWebExchange request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getSockPeerPort(ServerWebExchange request) {
    return null;
  }

  @Nullable
  @Override
  public String getSockHostAddr(ServerWebExchange request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getSockHostPort(ServerWebExchange request) {
    return null;
  }
}
