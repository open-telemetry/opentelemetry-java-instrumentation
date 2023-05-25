/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.grizzly;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import javax.annotation.Nullable;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;

final class GrizzlyNetAttributesGetter implements NetServerAttributesGetter<HttpRequestPacket> {

  @Override
  public String getTransport(HttpRequestPacket request) {
    Transport transport = request.getConnection().getTransport();
    if (transport instanceof TCPNIOTransport) {
      return IP_TCP;
    } else if (transport instanceof UDPNIOTransport) {
      return IP_UDP;
    }
    return null;
  }

  @Nullable
  @Override
  public String getProtocolName(HttpRequestPacket request) {
    String protocol = request.getProtocolString();
    if (protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(HttpRequestPacket request) {
    String protocol = request.getProtocolString();
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getHostName(HttpRequestPacket request) {
    return request.getLocalHost();
  }

  @Override
  public Integer getHostPort(HttpRequestPacket request) {
    return request.getServerPort();
  }

  @Nullable
  @Override
  public String getSockPeerAddr(HttpRequestPacket request) {
    return request.getRemoteAddress();
  }

  @Override
  public Integer getSockPeerPort(HttpRequestPacket request) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getSockHostAddr(HttpRequestPacket request) {
    return request.getLocalAddress();
  }

  @Override
  public Integer getSockHostPort(HttpRequestPacket request) {
    return request.getLocalPort();
  }
}
