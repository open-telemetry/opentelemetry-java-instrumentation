/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.netty.common.NettyConnectRequest;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class NettyConnectNetAttributesExtractor
    extends InetSocketAddressNetClientAttributesExtractor<NettyConnectRequest, Channel> {

  @Nullable
  @Override
  public InetSocketAddress getAddress(NettyConnectRequest request, @Nullable Channel channel) {
    SocketAddress remoteAddress = null;
    if (channel != null) {
      remoteAddress = channel.remoteAddress();
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
  public String transport(NettyConnectRequest request, @Nullable Channel channel) {
    return channel instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }
}
