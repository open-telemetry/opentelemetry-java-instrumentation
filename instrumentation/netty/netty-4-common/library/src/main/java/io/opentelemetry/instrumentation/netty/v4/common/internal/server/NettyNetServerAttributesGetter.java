/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.server;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.socket.DatagramChannel;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.instrumentation.netty.v4.common.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class NettyNetServerAttributesGetter
    implements NetServerAttributesGetter<HttpRequestAndChannel> {

  @Override
  public String getTransport(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Override
  public String getProtocolName(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.request().getProtocolVersion().protocolName();
  }

  @Override
  public String getProtocolVersion(HttpRequestAndChannel requestAndChannel) {
    HttpVersion version = requestAndChannel.request().getProtocolVersion();
    return version.majorVersion() + "." + version.minorVersion();
  }

  @Nullable
  @Override
  public String getHostName(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Nullable
  @Override
  public Integer getHostPort(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getPeerSocketAddress(HttpRequestAndChannel requestAndChannel) {
    SocketAddress address = requestAndChannel.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getHostSocketAddress(HttpRequestAndChannel requestAndChannel) {
    SocketAddress address = requestAndChannel.channel().localAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
