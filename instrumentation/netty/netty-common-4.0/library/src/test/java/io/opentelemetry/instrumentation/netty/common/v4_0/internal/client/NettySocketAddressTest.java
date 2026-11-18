/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.client;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.channel.Channel;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.netty.common.internal.NettyConnectionRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.NettyCommonRequest;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.server.NettyHttpServerAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

class NettySocketAddressTest {

  @Test
  @EnabledForJreRange(min = JRE.JAVA_16)
  void httpPeerAndTransportSurviveChannelClose() throws Exception {
    SocketAddress peer = unixAddress();
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(peer);
    NettyCommonRequest request =
        NettyCommonRequest.create(
            new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/"), channel);
    when(channel.remoteAddress()).thenReturn(null);

    NettyHttpClientAttributesGetter clientGetter = new NettyHttpClientAttributesGetter();
    assertThat(clientGetter.getNetworkPeerSocketAddress(request, null)).isSameAs(peer);
    assertThat(clientGetter.getNetworkTransport(request, null)).isEqualTo("unix");

    NettyHttpServerAttributesGetter serverGetter = new NettyHttpServerAttributesGetter();
    assertThat(serverGetter.getNetworkPeerSocketAddress(request, null)).isSameAs(peer);
    assertThat(serverGetter.getNetworkTransport(request, null)).isEqualTo("unix");
  }

  @Test
  @EnabledForJreRange(min = JRE.JAVA_16)
  void sslPeerAndTransportSurviveChannelClose() throws Exception {
    SocketAddress peer = unixAddress();
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(peer);
    NettySslRequest request = NettySslRequest.create(channel);
    when(channel.remoteAddress()).thenReturn(null);

    NettySslNetAttributesGetter getter = new NettySslNetAttributesGetter();
    assertThat(getter.getNetworkPeerSocketAddress(request, null)).isSameAs(peer);
    assertThat(getter.getNetworkTransport(request, null)).isEqualTo("unix");
  }

  @Test
  void connectKeepsLogicalServerSeparateFromSocketPeer() {
    SocketAddress peer = mock(SocketAddress.class);
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(peer);
    NettyConnectionRequest request =
        NettyConnectionRequest.connect(InetSocketAddress.createUnresolved("server.invalid", 8080));

    NettyConnectHttpAttributesGetter getter = new NettyConnectHttpAttributesGetter();
    assertThat(getter.getServerAddress(request)).isEqualTo("server.invalid");
    assertThat(getter.getServerPort(request)).isEqualTo(8080);
    assertThat(getter.getNetworkPeerSocketAddress(request, channel)).isSameAs(peer);
    assertThat(getter.getNetworkPeerSocketAddress(request, null)).isNull();
    assertThat(getter.getNetworkTransport(request, null)).isNull();
  }

  private static SocketAddress unixAddress() throws Exception {
    return (SocketAddress)
        Class.forName("java.net.UnixDomainSocketAddress")
            .getMethod("of", String.class)
            .invoke(null, "peer.sock");
  }
}
