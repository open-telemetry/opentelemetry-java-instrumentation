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
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.RedisCommand;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.unix.DomainSocketAddress;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.rx.LettuceReactiveCommandContext;
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
  void peerAccessDoesNotInstallStateOnAnUnownedCommand() {
    RedisCommand<?, ?, ?> command = new Command<>(CommandType.GET, null);

    assertThat(LettuceSingletons.commandPeerAddress(command)).isNull();

    LettuceSingletons.recordCommandPeer(command, new InetSocketAddress("localhost", PORT));

    assertThat(LettuceSingletons.commandPeerAddress(command)).isNull();
  }

  @Test
  void addressAttachmentDoesNotInstallStateOnAnUnownedCommand() {
    RedisCommand<?, ?, ?> command = new Command<>(CommandType.GET, null);

    LettuceSingletons.attachAddress(command, mock(StatefulConnection.class));

    assertThat(LettuceSingletons.commandPeerAddress(command)).isNull();
  }

  @Test
  void commandThatDoesNotExpectResponseDropsSelectedAddress() throws UnknownHostException {
    RedisCommand<?, ?, ?> command = new Command<>(CommandType.DEBUG, null);
    LettuceSingletons.initializeCommandPeer(command);
    LettuceSingletons.recordCommandPeer(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void commandKeepsConfiguredServerAddressWhenPeerIsUnknown() {
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.COMMAND_ADDRESS.set(
        command, InetSocketAddress.createUnresolved("redis.example", PORT));
    LettuceSingletons.COMMAND_TARGET.set(
        command, RedisServerTarget.ofHostAndPort("redis.example", PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getServerAddress(command)).isEqualTo("redis.example");
    assertThat(getter.getServerPort(command)).isEqualTo(emitStableDatabaseSemconv() ? null : PORT);
    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void batchUsesResolvedSelectedAddress() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(singletonList(commandWithPeer(address)), null, null, null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(request.getPeerAddress()).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.3" : null);
    assertThat(getter.getNetworkPeerPort(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void batchDropsUnresolvedSelectedAddress() {
    LettuceBatchRequest request =
        LettuceBatchRequest.create(
            singletonList(
                commandWithPeer(InetSocketAddress.createUnresolved("redis.example", PORT))),
            null,
            null,
            null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void batchUsesCommonFinalAddressAfterRetry() throws UnknownHostException {
    InetSocketAddress firstAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    InetSocketAddress finalAddress =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT);
    RedisCommand<?, ?, ?> firstCommand = commandWithPeer(firstAddress);
    RedisCommand<?, ?, ?> secondCommand = commandWithPeer(finalAddress);
    LettuceSingletons.recordCommandPeer(firstCommand, finalAddress);
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
    RedisCommand<?, ?, ?> firstCommand = commandWithPeer(firstAddress);
    RedisCommand<?, ?, ?> secondCommand = commandWithPeer(firstAddress);
    LettuceSingletons.recordCommandPeer(firstCommand, secondAddress);
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

    LettuceSingletons.initializeCommandPeer(wrapper);
    LettuceCommandOutboundHandler.recordCommandPeers(singletonList(wrapper), address);

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
              () -> {
                LettuceReactiveCommandContext.enter(firstCommand);
                try {
                  RedisCommand<?, ?, ?> command =
                      requireNonNull(LettuceReactiveCommandContext.current());
                  LettuceSingletons.recordCommandPeer(command, firstAddress);
                  ready.countDown();
                  release.await();
                  return LettuceSingletons.commandPeerAddress(command);
                } finally {
                  LettuceReactiveCommandContext.exit();
                }
              });
      Future<SocketAddress> second =
          executor.submit(
              () -> {
                LettuceReactiveCommandContext.enter(secondCommand);
                try {
                  RedisCommand<?, ?, ?> command =
                      requireNonNull(LettuceReactiveCommandContext.current());
                  LettuceSingletons.recordCommandPeer(command, secondAddress);
                  ready.countDown();
                  release.await();
                  return LettuceSingletons.commandPeerAddress(command);
                } finally {
                  LettuceReactiveCommandContext.exit();
                }
              });

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
  void concurrentSpanStartHasOneWinner() throws Exception {
    AsyncCommand<String, String, String> command = new AsyncCommand<>(command());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first =
          executor.submit(
              () -> {
                ready.countDown();
                release.await();
                return LettuceSingletons.markCommandSpanStarted(command);
              });
      Future<Boolean> second =
          executor.submit(
              () -> {
                ready.countDown();
                release.await();
                return LettuceSingletons.markCommandSpanStarted(command);
              });

      assertThat(ready.await(10, SECONDS)).isTrue();
      release.countDown();

      assertThat(asList(first.get(10, SECONDS), second.get(10, SECONDS)))
          .containsExactlyInAnyOrder(true, false);
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void initializingCommandPeerDoesNotResetExistingState() {
    AsyncCommand<String, String, String> command = new AsyncCommand<>(command());
    LettuceSingletons.initializeCommandPeer(command);

    assertThat(LettuceSingletons.markCommandSpanStarted(command)).isTrue();

    LettuceSingletons.initializeCommandPeer(command);

    assertThat(LettuceSingletons.markCommandSpanStarted(command)).isFalse();
  }

  @Test
  void newWrapperDoesNotReusePreviousPeer() throws UnknownHostException {
    RedisCommand<String, String, String> command = command();
    AsyncCommand<String, String, String> firstWrapper = new AsyncCommand<>(command);
    InetSocketAddress first =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    InetSocketAddress second =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT);

    LettuceSingletons.initializeCommandPeer(firstWrapper);
    LettuceCommandOutboundHandler.recordCommandPeers(firstWrapper, first);
    AsyncCommand<String, String, String> replayWrapper = new AsyncCommand<>(command);
    LettuceSingletons.initializeCommandPeer(replayWrapper);

    assertThat(LettuceSingletons.commandPeerAddress(firstWrapper)).isEqualTo(first);
    assertThat(LettuceSingletons.commandPeerAddress(replayWrapper)).isNull();

    LettuceCommandOutboundHandler.recordCommandPeers(singletonList(replayWrapper), second);

    assertThat(LettuceSingletons.commandPeerAddress(firstWrapper)).isEqualTo(first);
    assertThat(LettuceSingletons.commandPeerAddress(replayWrapper)).isEqualTo(second);
  }

  private static RedisCommand<String, String, String> command() {
    RedisCommand<String, String, String> command = new Command<>(CommandType.GET, null);
    LettuceSingletons.initializeCommandPeer(command);
    return command;
  }

  private static RedisCommand<?, ?, ?> commandWithPeer(SocketAddress address) {
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.recordCommandPeer(command, address);
    return command;
  }

  private static Stream<Arguments> resolvedAddresses() throws UnknownHostException {
    return Stream.of(
        argumentSet("ipv4", InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), "10.1.2.3"),
        argumentSet(
            "ipv6",
            InetAddress.getByAddress(
                new byte[] {0x20, 0x01, 0x0d, (byte) 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}),
            "2001:db8:0:0:0:0:0:1"));
  }
}
