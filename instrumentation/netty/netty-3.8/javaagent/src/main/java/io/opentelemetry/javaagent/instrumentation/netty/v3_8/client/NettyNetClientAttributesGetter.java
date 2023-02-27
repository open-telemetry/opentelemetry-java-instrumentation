/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.handler.codec.http.HttpResponse;

final class NettyNetClientAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<HttpRequestAndChannel, HttpResponse> {

  @Override
  public String getTransport(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return requestAndChannel.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Nullable
  @Override
  public String getPeerName(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.channel().getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
