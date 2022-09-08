/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class NettyConnectNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<NettyConnectionRequest, Channel> {

  @Override
  public String transport(NettyConnectionRequest request, @Nullable Channel channel) {
    return channel instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Nullable
  @Override
  public String peerName(NettyConnectionRequest request, @Nullable Channel channel) {
    SocketAddress requestedAddress = request.remoteAddressOnStart();
    if (requestedAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) requestedAddress).getHostString();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(NettyConnectionRequest request, @Nullable Channel channel) {
    SocketAddress requestedAddress = request.remoteAddressOnStart();
    if (requestedAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) requestedAddress).getPort();
    }
    return null;
  }

  @Nullable
  @Override
  protected InetSocketAddress getPeerSocketAddress(
      NettyConnectionRequest request, @Nullable Channel channel) {
    if (channel == null) {
      return null;
    }
    SocketAddress remoteAddress = channel.remoteAddress();
    if (remoteAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) remoteAddress;
    }
    return null;
  }
}
