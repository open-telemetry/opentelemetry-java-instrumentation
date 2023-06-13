/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.instrumentation.netty.v4.common.internal.ChannelUtil;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class NettyConnectNetAttributesGetter
    implements NetClientAttributesGetter<NettyConnectionRequest, Channel> {

  @Override
  public String getTransport(NettyConnectionRequest request, @Nullable Channel channel) {
    return channel instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Override
  public String getNetworkTransport(NettyConnectionRequest request, @Nullable Channel channel) {
    return ChannelUtil.getNetworkTransport(channel);
  }

  @Nullable
  @Override
  public String getServerAddress(NettyConnectionRequest request) {
    SocketAddress requestedAddress = request.remoteAddressOnStart();
    if (requestedAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) requestedAddress).getHostString();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(NettyConnectionRequest request) {
    SocketAddress requestedAddress = request.remoteAddressOnStart();
    if (requestedAddress instanceof InetSocketAddress) {
      return ((InetSocketAddress) requestedAddress).getPort();
    }
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
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
