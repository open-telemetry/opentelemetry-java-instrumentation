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
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.UDPNIOTransport;

final class GrizzlyNetAttributesGetter
    implements NetServerAttributesGetter<HttpRequestPacket, HttpResponsePacket> {

  @Override
  public String getTransport(HttpRequestPacket request) {
    Transport transport = request.getConnection().getTransport();
    if (transport instanceof TCPNIOTransport) {
      return IP_TCP;
    }
    if (transport instanceof UDPNIOTransport) {
      return IP_UDP;
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkTransport(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    Transport transport = request.getConnection().getTransport();
    if (transport instanceof TCPNIOTransport) {
      return "tcp";
    } else if (transport instanceof UDPNIOTransport) {
      return "udp";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    String protocol = request.getProtocolString();
    if (protocol.startsWith("HTTP/")) {
      return "http";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    String protocol = request.getProtocolString();
    if (protocol.startsWith("HTTP/")) {
      return protocol.substring("HTTP/".length());
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(HttpRequestPacket request) {
    // rely on the 'host' header parsing
    return null;
  }

  @Override
  public Integer getServerPort(HttpRequestPacket request) {
    // rely on the 'host' header parsing
    return null;
  }

  @Nullable
  @Override
  public String getClientSocketAddress(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return request.getRemoteAddress();
  }

  @Override
  public Integer getClientSocketPort(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return request.getRemotePort();
  }

  @Nullable
  @Override
  public String getServerSocketAddress(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return request.getLocalAddress();
  }

  @Override
  public Integer getServerSocketPort(
      HttpRequestPacket request, @Nullable HttpResponsePacket response) {
    return request.getLocalPort();
  }
}
