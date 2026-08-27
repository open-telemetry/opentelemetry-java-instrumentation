/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.RedisCommand;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

class LettuceNetworkAttributesGetterTest {

  private static final int PORT = 6379;

  @Test
  void commandUsesResolvedSelectedAddress() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.recordCommandPeer(command, address);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isEqualTo("10.1.2.3");
    assertThat(getter.getNetworkPeerPort(command, null)).isEqualTo(PORT);
  }

  @Test
  void commandDropsUnresolvedSelectedAddress() {
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.recordCommandPeer(
        command, InetSocketAddress.createUnresolved("redis.example", PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void commandThatDoesNotExpectResponseDropsSelectedAddress() throws UnknownHostException {
    RedisCommand<?, ?, ?> command = new Command<>(CommandType.DEBUG, null);
    LettuceSingletons.recordCommandPeer(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void batchOmitsSelectedAddress() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.recordCommandPeer(command, address);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(singletonList(command), null, null, null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void commandDropsAmbiguousSelectedAddress() throws UnknownHostException {
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.recordCommandPeer(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT));
    LettuceSingletons.recordCommandPeer(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void collectionWriteCapturesExactPeer() throws UnknownHostException {
    RedisCommand<String, String, String> command = command();
    AsyncCommand<String, String, String> wrapper = new AsyncCommand<>(command);
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);

    LettuceSingletons.linkCommandPeer(wrapper);
    LettuceCommandOutboundHandler.recordCommands(singletonList(wrapper), address);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
    assertThat(getter.getNetworkPeerAddress(wrapper, null)).isEqualTo("10.1.2.3");
    assertThat(getter.getNetworkPeerPort(wrapper, null)).isEqualTo(PORT);
  }

  @Test
  void outboundWriteContinuesWhenRecordingFails() {
    InetSocketAddress address = new InetSocketAddress("localhost", PORT);
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(address);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    when(context.channel()).thenReturn(channel);
    ChannelPromise promise = mock(ChannelPromise.class);
    Collection<?> message = mock(Collection.class);
    when(message.iterator()).thenThrow(new IllegalStateException("test"));

    new LettuceCommandOutboundHandler().write(context, message, promise);

    verify(context).write(message, promise);
  }

  @Test
  void collectionReplayToDifferentPeerIsAmbiguous() throws UnknownHostException {
    RedisCommand<String, String, String> command = command();
    AsyncCommand<String, String, String> firstWrapper = new AsyncCommand<>(command);
    InetSocketAddress first =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    InetSocketAddress second =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT);

    LettuceSingletons.linkCommandPeer(firstWrapper);
    LettuceCommandOutboundHandler.recordCommands(firstWrapper, first);
    AsyncCommand<String, String, String> replayWrapper = new AsyncCommand<>(command);
    LettuceSingletons.linkCommandPeer(replayWrapper);
    LettuceCommandOutboundHandler.recordCommands(singletonList(replayWrapper), second);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
    assertThat(getter.getNetworkPeerAddress(firstWrapper, null)).isNull();
    assertThat(getter.getNetworkPeerPort(firstWrapper, null)).isNull();
    assertThat(getter.getNetworkPeerAddress(replayWrapper, null)).isNull();
    assertThat(getter.getNetworkPeerPort(replayWrapper, null)).isNull();
  }

  @Test
  void concurrentLinkAndRedirectPreserveAmbiguity() throws Exception {
    RedisCommand<String, String, String> command = command();
    AsyncCommand<String, String, String> firstWrapper = new AsyncCommand<>(command);
    AsyncCommand<String, String, String> redirectWrapper = new AsyncCommand<>(command);
    InetSocketAddress first =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    InetSocketAddress second =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT);
    LettuceSingletons.recordCommandPeer(firstWrapper, first);

    CountDownLatch start = new CountDownLatch(1);
    CompletableFuture<Void> link =
        CompletableFuture.runAsync(
            () -> {
              await(start);
              LettuceSingletons.linkCommandPeer(redirectWrapper);
            });
    CompletableFuture<Void> redirect =
        CompletableFuture.runAsync(
            () -> {
              await(start);
              LettuceSingletons.recordCommandPeer(command, second);
            });
    start.countDown();
    CompletableFuture.allOf(link, redirect).get();

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
    assertThat(getter.getNetworkPeerAddress(firstWrapper, null)).isNull();
    assertThat(getter.getNetworkPeerAddress(redirectWrapper, null)).isNull();
  }

  private static RedisCommand<String, String, String> command() {
    return new Command<>(CommandType.GET, null);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError(e);
    }
  }
}
