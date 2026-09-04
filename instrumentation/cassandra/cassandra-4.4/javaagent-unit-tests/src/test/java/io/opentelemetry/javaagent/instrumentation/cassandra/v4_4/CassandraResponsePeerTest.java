/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.cassandra.v4_4;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datastax.oss.driver.api.core.cql.ExecutionInfo;
import com.datastax.oss.protocol.internal.Frame;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.opentelemetry.instrumentation.cassandra.v4_4.internal.CassandraNetworkPeer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class CassandraResponsePeerTest {

  @Test
  void correlatesMultiplexedResponsesWithTheirChannels() throws UnknownHostException {
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

    assertThat(CassandraNetworkPeer.getExecutionInfoPeer(firstExecutionInfo)).isEqualTo(firstPeer);
    assertThat(CassandraNetworkPeer.getExecutionInfoPeer(secondExecutionInfo))
        .isEqualTo(secondPeer);
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

    assertThat(CassandraNetworkPeer.getExecutionInfoPeer(unresolvedExecutionInfo)).isNull();
    assertThat(CassandraNetworkPeer.getExecutionInfoPeer(nonInetExecutionInfo)).isNull();
  }

  @Test
  void supportsPackageIndependentPublicContextInterfaces() throws UnknownHostException {
    InetSocketAddress peer = resolved(39042);
    Frame responseFrame = frame(1);
    ExecutionInfo executionInfo = mock(ExecutionInfo.class);

    InFlightHandlerInstrumentation.ChannelReadAdvice.onEnter(
        new TestContext(new TestChannel(peer)), responseFrame);
    DefaultExecutionInfoInstrumentation.ConstructorAdvice.onExit(executionInfo, responseFrame);

    assertThat(CassandraNetworkPeer.getExecutionInfoPeer(executionInfo)).isEqualTo(peer);
  }

  @Test
  void finalRetryResponseKeepsItsChannelPeer() throws UnknownHostException {
    ChannelHandlerContext firstContext = mock(ChannelHandlerContext.class);
    ChannelHandlerContext retryContext = mock(ChannelHandlerContext.class);
    Channel firstChannel = mock(Channel.class);
    Channel retryChannel = mock(Channel.class);
    Frame firstResponse = frame(1);
    Frame retryResponse = frame(1);
    ExecutionInfo firstExecutionInfo = mock(ExecutionInfo.class);
    ExecutionInfo retryExecutionInfo = mock(ExecutionInfo.class);
    InetSocketAddress firstPeer = resolved(19042);
    InetSocketAddress retryPeer = resolved(29042);
    when(firstContext.channel()).thenReturn(firstChannel);
    when(retryContext.channel()).thenReturn(retryChannel);
    when(firstChannel.remoteAddress()).thenReturn(firstPeer);
    when(retryChannel.remoteAddress()).thenReturn(retryPeer);

    InFlightHandlerInstrumentation.ChannelReadAdvice.onEnter(firstContext, firstResponse);
    InFlightHandlerInstrumentation.ChannelReadAdvice.onEnter(retryContext, retryResponse);
    DefaultExecutionInfoInstrumentation.ConstructorAdvice.onExit(firstExecutionInfo, firstResponse);
    DefaultExecutionInfoInstrumentation.ConstructorAdvice.onExit(retryExecutionInfo, retryResponse);

    assertThat(CassandraNetworkPeer.getExecutionInfoPeer(firstExecutionInfo)).isEqualTo(firstPeer);
    assertThat(CassandraNetworkPeer.getExecutionInfoPeer(retryExecutionInfo)).isEqualTo(retryPeer);
  }

  private static InetSocketAddress resolved(int port) throws UnknownHostException {
    return new InetSocketAddress(InetAddress.getByAddress(new byte[] {127, 0, 0, 1}), port);
  }

  private static Frame frame(int streamId) {
    return new Frame(4, false, streamId, false, null, 0, 0, emptyMap(), emptyList(), null);
  }

  public interface PublicContext {
    PublicChannel channel();
  }

  public interface PublicChannel {
    SocketAddress remoteAddress();
  }

  private interface InternalContext extends PublicContext {}

  private interface InternalChannel extends PublicChannel {}

  private static class TestContext implements InternalContext {
    private final InternalChannel channel;

    private TestContext(InternalChannel channel) {
      this.channel = channel;
    }

    @Override
    public InternalChannel channel() {
      return channel;
    }
  }

  private static class TestChannel implements InternalChannel {
    private final SocketAddress remoteAddress;

    private TestChannel(SocketAddress remoteAddress) {
      this.remoteAddress = remoteAddress;
    }

    @Override
    public SocketAddress remoteAddress() {
      return remoteAddress;
    }
  }
}
