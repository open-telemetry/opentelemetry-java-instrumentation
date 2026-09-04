/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
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
import io.netty.channel.unix.DomainSocketAddress;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LettuceNetworkAttributesGetterTest {

  private static final int PORT = 6379;

  @ParameterizedTest
  @MethodSource("resolvedAddresses")
  void commandUsesResolvedSelectedAddress(InetAddress inetAddress, String expectedAddress) {
    InetSocketAddress address = new InetSocketAddress(inetAddress, PORT);
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.recordCommandPeer(command, address);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(LettuceSingletons.commandPeerAddress(command)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? expectedAddress : null);
    assertThat(getter.getNetworkPeerPort(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
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
  void batchUsesResolvedSelectedAddress() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.recordCommandPeer(command, address);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(singletonList(command), null, null, null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(request.getPeerAddress()).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.3" : null);
    assertThat(getter.getNetworkPeerPort(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void batchUsesCommonFinalAddressAfterRetry() throws UnknownHostException {
    InetSocketAddress firstAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    InetSocketAddress finalAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT);
    RedisCommand<?, ?, ?> firstCommand = command();
    RedisCommand<?, ?, ?> secondCommand = command();
    LettuceSingletons.recordCommandPeer(firstCommand, firstAddress);
    LettuceSingletons.recordCommandPeer(firstCommand, finalAddress);
    LettuceSingletons.recordCommandPeer(secondCommand, finalAddress);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(asList(firstCommand, secondCommand), null, null, null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.4" : null);
    assertThat(getter.getNetworkPeerPort(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void batchDropsDifferentFinalAddressesAfterRetry() throws UnknownHostException {
    InetSocketAddress firstAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    InetSocketAddress secondAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT);
    RedisCommand<?, ?, ?> firstCommand = command();
    RedisCommand<?, ?, ?> secondCommand = command();
    LettuceSingletons.recordCommandPeer(firstCommand, firstAddress);
    LettuceSingletons.recordCommandPeer(firstCommand, secondAddress);
    LettuceSingletons.recordCommandPeer(secondCommand, firstAddress);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(asList(firstCommand, secondCommand), null, null, null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void commandRetryUsesLastSelectedAddress() throws UnknownHostException {
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.recordCommandPeer(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT));
    LettuceSingletons.recordCommandPeer(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.4" : null);
    assertThat(getter.getNetworkPeerPort(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void outboundWriteCapturesDomainSocketPath() {
    DomainSocketAddress address = new DomainSocketAddress("/var/run/redis.sock");
    RedisCommand<?, ?, ?> command = command();
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(address);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    when(context.channel()).thenReturn(channel);
    ChannelPromise promise = mock(ChannelPromise.class);

    new LettuceCommandOutboundHandler().write(context, command, promise);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
    assertThat(LettuceSingletons.commandPeerAddress(command)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "/var/run/redis.sock" : null);
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
    verify(context).write(command, promise);
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
    assertThat(LettuceSingletons.commandPeerAddress(wrapper)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(wrapper, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.3" : null);
    assertThat(getter.getNetworkPeerPort(wrapper, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
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
  void outboundWriteRecordsPeerBeforePromiseCompletes() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    RedisCommand<?, ?, ?> command = command();
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(address);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    when(context.channel()).thenReturn(channel);
    ChannelPromise promise = mock(ChannelPromise.class);

    new LettuceCommandOutboundHandler().write(context, command, promise);

    assertThat(LettuceSingletons.commandPeerAddress(command)).isEqualTo(address);
    verify(context).write(command, promise);
  }

  @Test
  void concurrentReactiveSubscriptionsKeepDistinctPeers() throws Exception {
    InetSocketAddress firstAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    InetSocketAddress secondAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT);
    RedisCommand<?, ?, ?> firstCommand = command();
    RedisCommand<?, ?, ?> secondCommand = command();

    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<SocketAddress> first =
          executor.submit(
              () -> recordReactiveSubscriptionPeer(firstCommand, firstAddress, ready, release));
      Future<SocketAddress> second =
          executor.submit(
              () -> recordReactiveSubscriptionPeer(secondCommand, secondAddress, ready, release));

      assertThat(ready.await(10, SECONDS)).isTrue();
      release.countDown();

      assertThat(first.get(10, SECONDS)).isEqualTo(firstAddress);
      assertThat(second.get(10, SECONDS)).isEqualTo(secondAddress);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void newWrapperDoesNotReusePreviousPeer() throws UnknownHostException {
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

    assertThat(LettuceSingletons.commandPeerAddress(firstWrapper)).isEqualTo(first);
    assertThat(LettuceSingletons.commandPeerAddress(replayWrapper)).isNull();

    LettuceCommandOutboundHandler.recordCommands(singletonList(replayWrapper), second);

    assertThat(LettuceSingletons.commandPeerAddress(firstWrapper)).isEqualTo(first);
    assertThat(LettuceSingletons.commandPeerAddress(replayWrapper)).isEqualTo(second);
  }

  private static RedisCommand<String, String, String> command() {
    return new Command<>(CommandType.GET, null);
  }

  private static Stream<Arguments> resolvedAddresses() throws UnknownHostException {
    return Stream.of(
        argumentSet(
            "ipv4", InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), "10.1.2.3"),
        argumentSet(
            "ipv6",
            InetAddress.getByAddress(
                new byte[] {
                  0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1
                }),
            "2001:db8:0:0:0:0:0:1"));
  }

  private static SocketAddress recordReactiveSubscriptionPeer(
      RedisCommand<?, ?, ?> subscriptionCommand,
      InetSocketAddress address,
      CountDownLatch ready,
      CountDownLatch release)
      throws InterruptedException {
    LettuceSingletons.enterReactiveCommand(subscriptionCommand);
    try {
      RedisCommand<?, ?, ?> command = requireNonNull(LettuceSingletons.currentReactiveCommand());
      LettuceSingletons.recordCommandPeer(command, address);
      ready.countDown();
      release.await();
      return LettuceSingletons.commandPeerAddress(command);
    } finally {
      LettuceSingletons.exitReactiveCommand();
    }
  }
}
