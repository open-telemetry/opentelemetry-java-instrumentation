/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.server;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import io.opentelemetry.instrumentation.netty.v4.common.internal.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class NettyNetServerAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<HttpRequestAndChannel> {

  @Override
  public String transport(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Nullable
  @Override
  public String hostName(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Nullable
  @Override
  public Integer hostPort(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(HttpRequestAndChannel requestAndChannel) {
    SocketAddress address = requestAndChannel.remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Nullable
  @Override
  protected InetSocketAddress getHostSocketAddress(HttpRequestAndChannel requestAndChannel) {
    SocketAddress address = requestAndChannel.channel().localAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
