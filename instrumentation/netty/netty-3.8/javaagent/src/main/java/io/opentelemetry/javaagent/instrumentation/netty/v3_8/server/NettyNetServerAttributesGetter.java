/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.server;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import org.jboss.netty.channel.socket.DatagramChannel;

final class NettyNetServerAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<HttpRequestAndChannel> {

  @Override
  @Nullable
  public String transport(HttpRequestAndChannel requestAndChannel) {
    return requestAndChannel.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Override
  @Nullable
  public InetSocketAddress getAddress(HttpRequestAndChannel requestAndChannel) {
    SocketAddress address = requestAndChannel.channel().getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
