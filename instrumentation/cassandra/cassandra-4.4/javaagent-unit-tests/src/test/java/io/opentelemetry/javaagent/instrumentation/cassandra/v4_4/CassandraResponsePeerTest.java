/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import static io.opentelemetry.instrumentation.cassandra.v4_4.internal.CassandraNetworkPeer.get;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.protocol.internal.Frame;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;

class CassandraResponsePeerTest {

  @Test
  void correlatesMultiplexedResponsesWithTheirChannels() throws Exception {
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    Channel channel = mock(Channel.class);
    Frame firstFrame = frame(1);
    Frame secondFrame = frame(2);
    ExecutionInfo firstExecutionInfo = mock(ExecutionInfo.class);
    ExecutionInfo secondExecutionInfo = mock(ExecutionInfo.class);
    InetSocketAddress firstPeer = resolved(19042);
    InetSocketAddress secondPeer = resolved(29042);
    when(context.channel()).thenReturn(channel);
    when(channel.remoteAddress()).thenReturn(firstPeer, secondPeer);

    InFlightHandlerInstrumentation.ChannelReadAdvice.onEnter(context, firstFrame);
    InFlightHandlerInstrumentation.ChannelReadAdvice.onEnter(context, secondFrame);
    DefaultExecutionInfoInstrumentation.ConstructorAdvice.onExit(secondExecutionInfo, secondFrame);
    DefaultExecutionInfoInstrumentation.ConstructorAdvice.onExit(firstExecutionInfo, firstFrame);

    assertThat(get(firstExecutionInfo)).isEqualTo(firstPeer);
    assertThat(get(secondExecutionInfo)).isEqualTo(secondPeer);
  }

  @Test
  void omitsUnresolvedAndNonInetChannelAddresses() {
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    Channel channel = mock(Channel.class);
    Frame unresolvedFrame = frame(1);
    Frame nonInetFrame = frame(2);
    ExecutionInfo unresolvedExecutionInfo = mock(ExecutionInfo.class);
    ExecutionInfo nonInetExecutionInfo = mock(ExecutionInfo.class);
    when(context.channel()).thenReturn(channel);
    when(channel.remoteAddress())
        .thenReturn(
            InetSocketAddress.createUnresolved("cassandra.example.com", 9042),
            new SocketAddress() {});

    InFlightHandlerInstrumentation.ChannelReadAdvice.onEnter(context, unresolvedFrame);
    InFlightHandlerInstrumentation.ChannelReadAdvice.onEnter(context, nonInetFrame);
    DefaultExecutionInfoInstrumentation.ConstructorAdvice.onExit(
        unresolvedExecutionInfo, unresolvedFrame);
    DefaultExecutionInfoInstrumentation.ConstructorAdvice.onExit(
        nonInetExecutionInfo, nonInetFrame);

    assertThat(get(unresolvedExecutionInfo)).isNull();
    assertThat(get(nonInetExecutionInfo)).isNull();
  }

  private static InetSocketAddress resolved(int port) throws Exception {
    return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
  }

  private static Frame frame(int streamId) {
    return new Frame(4, false, streamId, false, null, 0, 0, emptyMap(), emptyList(), null);
  }
}
