/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_0.server;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.springframework.http.server.reactive.ServerHttpRequest;

enum SpringWebfluxNetAttributesGetter implements NetServerAttributesGetter<ServerHttpRequest> {
  INSTANCE;

  @Override
  public String getTransport(ServerHttpRequest request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getHostName(ServerHttpRequest request) {
    return request.getURI().getHost();
  }

  @Nullable
  @Override
  public Integer getHostPort(ServerHttpRequest request) {
    int port = request.getURI().getPort();
    return port == -1 ? null : port;
  }

  @Nullable
  @Override
  public String getSockFamily(ServerHttpRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getSockPeerAddr(ServerHttpRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getSockPeerPort(ServerHttpRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getSockHostAddr(ServerHttpRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getSockHostPort(ServerHttpRequest request) {
    return null;
  }
}
