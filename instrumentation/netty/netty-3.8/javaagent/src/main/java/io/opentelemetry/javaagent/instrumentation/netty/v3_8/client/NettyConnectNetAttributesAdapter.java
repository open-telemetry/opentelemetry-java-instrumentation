/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesAdapter;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectionRequest;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.DatagramChannel;

final class NettyConnectNetAttributesAdapter
    extends InetSocketAddressNetClientAttributesAdapter<NettyConnectionRequest, Channel> {

  @Nullable
  @Override
  public InetSocketAddress getAddress(NettyConnectionRequest request, @Nullable Channel channel) {
    SocketAddress remoteAddress = null;
    if (channel != null) {
      remoteAddress = channel.getRemoteAddress();
    }
    // remote address on end() may be null when connection hasn't been established
    if (remoteAddress == null) {
      remoteAddress = request.remoteAddressOnStart();
    }
    if (remoteAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) remoteAddress;
    }
    return null;
  }

  @Override
  public String transport(NettyConnectionRequest request, @Nullable Channel channel) {
    return channel instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }
}
