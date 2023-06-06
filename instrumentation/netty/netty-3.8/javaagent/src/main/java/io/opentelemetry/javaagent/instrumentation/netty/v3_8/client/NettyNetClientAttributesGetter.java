/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.HttpRequestAndChannel;
import io.opentelemetry.javaagent.instrumentation.netty.v3_8.util.ChannelUtil;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import org.jboss.netty.channel.socket.DatagramChannel;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpVersion;

final class NettyNetClientAttributesGetter
    implements NetClientAttributesGetter<HttpRequestAndChannel, HttpResponse> {

  @Override
  public String getTransport(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return requestAndChannel.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Override
  public String getNetworkTransport(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    return ChannelUtil.getNetworkTransport(requestAndChannel.channel());
  }

  @Override
  public String getNetworkProtocolName(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse httpResponse) {
    return requestAndChannel.request().getProtocolVersion().getProtocolName();
  }

  @Override
  public String getNetworkProtocolVersion(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse httpResponse) {
    HttpVersion version = requestAndChannel.request().getProtocolVersion();
    return version.getMajorVersion() + "." + version.getMinorVersion();
  }

  @Nullable
  @Override
  public String getServerAddress(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(HttpRequestAndChannel requestAndChannel) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getServerInetSocketAddress(
      HttpRequestAndChannel requestAndChannel, @Nullable HttpResponse response) {
    SocketAddress address = requestAndChannel.channel().getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
