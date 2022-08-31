/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;

final class GrizzlyNetAttributesGetter implements NetServerAttributesGetter<HttpRequestPacket> {

  @Override
  public String transport(HttpRequestPacket request) {
    return request.getConnection().getTransport() instanceof TCPNIOTransport ? IP_TCP : IP_UDP;
  }

  @Nullable
  @Override
  public String hostName(HttpRequestPacket request) {
    return request.getLocalHost();
  }

  @Override
  public Integer hostPort(HttpRequestPacket request) {
    return request.getServerPort();
  }

  @Nullable
  @Override
  public String sockFamily(HttpRequestPacket request) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerAddr(HttpRequestPacket request) {
    return request.getRemoteAddress();
  }

  @Override
  public Integer sockPeerPort(HttpRequestPacket request) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String sockHostAddr(HttpRequestPacket request) {
    return request.getLocalAddress();
  }

  @Override
  public Integer sockHostPort(HttpRequestPacket request) {
    return request.getLocalPort();
  }
}
