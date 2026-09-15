/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.protocol.AsyncCommand;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.DecoratedCommand;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.lettuce.core.protocol.RedisCommand;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.unix.DomainSocketAddress;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
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
    LettuceCommandPeer.record(command, address);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(LettuceCommandPeer.address(command)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? expectedAddress : null);
    assertThat(getter.getNetworkPeerPort(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void commandDropsUnresolvedSelectedAddress() {
    RedisCommand<?, ?, ?> command = command();
    LettuceCommandPeer.record(command, InetSocketAddress.createUnresolved("redis.example", PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void peerAccessDoesNotInstallStateOnAnUnownedCommand() {
    RedisCommand<?, ?, ?> command = new Command<>(CommandType.GET, null);

    assertThat(LettuceCommandPeer.address(command)).isNull();

    LettuceCommandPeer.record(command, new InetSocketAddress("localhost", PORT));

    assertThat(LettuceCommandPeer.address(command)).isNull();
  }

  @Test
  void addressAttachmentDoesNotInstallStateOnAnUnownedCommand() {
    RedisCommand<?, ?, ?> command = new Command<>(CommandType.GET, null);

    LettuceConnectionState.copy(mock(StatefulConnection.class), command);

    assertThat(LettuceCommandPeer.address(command)).isNull();
  }

  @Test
  @SuppressWarnings("unchecked")
  void decoratedCommandSharesDelegatePeerState() throws UnknownHostException {
    RedisCommand<String, String, String> delegate = command();
    RedisCommand<String, String, String> decorated =
        mock(RedisCommand.class, withSettings().extraInterfaces(DecoratedCommand.class));
    when(((DecoratedCommand<String, String, String>) decorated).getDelegate()).thenReturn(delegate);
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);

    LettuceCommandPeer.record(decorated, address);

    assertThat(LettuceCommandPeer.address(decorated)).isEqualTo(address);
    assertThat(LettuceCommandPeer.address(delegate)).isEqualTo(address);
  }

  @Test
  void commandThatDoesNotExpectResponseDropsSelectedAddress() throws UnknownHostException {
    RedisCommand<?, ?, ?> command = new Command<>(CommandType.DEBUG, null);
    LettuceCommandPeer.initialize(command);
    LettuceCommandPeer.record(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void commandKeepsConfiguredServerAddressWhenPeerIsUnknown() {
    RedisCommand<?, ?, ?> command = command();
    DefaultEndpoint endpoint = new DefaultEndpoint(ClientOptions.create());
    try {
      LettuceConnectionState.captureEndpoint(
          endpoint,
          InetSocketAddress.createUnresolved("redis.example", PORT),
          null,
          RedisServerTarget.ofHostAndPort("redis.example", PORT));
      LettuceConnectionState.copy(endpoint, command, null);

      LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

      assertThat(getter.getServerAddress(command)).isEqualTo("redis.example");
      assertThat(getter.getServerPort(command))
          .isEqualTo(emitStableDatabaseSemconv() ? null : PORT);
      assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
      assertThat(getter.getNetworkPeerPort(command, null)).isNull();
    } finally {
      endpoint.close();
    }
  }

  @Test
  void batchUsesResolvedSelectedAddress() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(singletonList(commandWithPeer(address)), null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(request.getPeerAddress()).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.3" : null);
    assertThat(getter.getNetworkPeerPort(request, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void batchKeepsCommandSnapshotWhenSourceListChanges() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    List<RedisCommand<?, ?, ?>> commands = new ArrayList<>();
    commands.add(commandWithPeer(address));
    LettuceBatchRequest request = LettuceBatchRequest.create(commands, null);

    commands.clear();

    assertThat(request.getPeerAddress()).isEqualTo(address);
  }

  @Test
  void batchDropsUnresolvedSelectedAddress() {
    LettuceBatchRequest request =
        LettuceBatchRequest.create(
            singletonList(
                commandWithPeer(InetSocketAddress.createUnresolved("redis.example", PORT))),
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
    LettuceCommandPeer.record(firstCommand, finalAddress);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(asList(firstCommand, secondCommand), null);

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
    LettuceCommandPeer.record(firstCommand, secondAddress);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(asList(firstCommand, secondCommand), null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void commandRetryUsesLastSelectedAddress() throws UnknownHostException {
    RedisCommand<?, ?, ?> command = command();
    LettuceCommandPeer.record(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT));
    LettuceCommandPeer.record(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.4" : null);
    assertThat(getter.getNetworkPeerPort(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void encoderCapturesDomainSocketPath() {
    DomainSocketAddress address = new DomainSocketAddress("/var/run/redis.sock");
    RedisCommand<?, ?, ?> command = command();
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(address);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    when(context.channel()).thenReturn(channel);

    LettuceCommandEncoderInstrumentation.EncodeAdvice.onEnter(context, command);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
    assertThat(LettuceCommandPeer.address(command)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "/var/run/redis.sock" : null);
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void collectionWriteCapturesExactPeer() throws UnknownHostException {
    RedisCommand<String, String, String> command = command();
    AsyncCommand<String, String, String> wrapper = new AsyncCommand<>(command);
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);

    LettuceCommandPeer.initialize(wrapper);
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(address);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    when(context.channel()).thenReturn(channel);
    LettuceCommandEncoderInstrumentation.EncodeAdvice.onEnter(context, singletonList(wrapper));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
    assertThat(LettuceCommandPeer.address(wrapper)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(wrapper, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.3" : null);
    assertThat(getter.getNetworkPeerPort(wrapper, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void encoderRecordsPeerBeforeEncoding() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    RedisCommand<?, ?, ?> command = command();
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(address);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    when(context.channel()).thenReturn(channel);

    LettuceCommandEncoderInstrumentation.EncodeAdvice.onEnter(context, command);

    assertThat(LettuceCommandPeer.address(command)).isEqualTo(address);
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
                return LettuceCommandPeer.markSpanStarted(command);
              });
      Future<Boolean> second =
          executor.submit(
              () -> {
                ready.countDown();
                release.await();
                return LettuceCommandPeer.markSpanStarted(command);
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
    LettuceCommandPeer.initialize(command);

    assertThat(LettuceCommandPeer.markSpanStarted(command)).isTrue();

    LettuceCommandPeer.initialize(command);

    assertThat(LettuceCommandPeer.markSpanStarted(command)).isFalse();
  }

  @Test
  void subclassConstructorInitializesCommandPeerOnce() {
    AsyncCommand<String, String, String> command = new SubclassAsyncCommand(command());

    assertThat(LettuceCommandPeer.markSpanStarted(command)).isTrue();
    assertThat(LettuceCommandPeer.markSpanStarted(command)).isFalse();
  }

  @Test
  void newWrapperDoesNotReusePreviousPeer() throws UnknownHostException {
    RedisCommand<String, String, String> command = command();
    AsyncCommand<String, String, String> firstWrapper = new AsyncCommand<>(command);
    InetSocketAddress first =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    InetSocketAddress second =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT);

    LettuceCommandPeer.initialize(firstWrapper);
    Channel firstChannel = mock(Channel.class);
    when(firstChannel.remoteAddress()).thenReturn(first);
    ChannelHandlerContext firstContext = mock(ChannelHandlerContext.class);
    when(firstContext.channel()).thenReturn(firstChannel);
    LettuceCommandEncoderInstrumentation.EncodeAdvice.onEnter(firstContext, firstWrapper);
    AsyncCommand<String, String, String> replayWrapper = new AsyncCommand<>(command);
    LettuceCommandPeer.initialize(replayWrapper);

    assertThat(LettuceCommandPeer.address(firstWrapper)).isEqualTo(first);
    assertThat(LettuceCommandPeer.address(replayWrapper)).isNull();

    Channel secondChannel = mock(Channel.class);
    when(secondChannel.remoteAddress()).thenReturn(second);
    ChannelHandlerContext secondContext = mock(ChannelHandlerContext.class);
    when(secondContext.channel()).thenReturn(secondChannel);
    LettuceCommandEncoderInstrumentation.EncodeAdvice.onEnter(
        secondContext, singletonList(replayWrapper));

    assertThat(LettuceCommandPeer.address(firstWrapper)).isEqualTo(first);
    assertThat(LettuceCommandPeer.address(replayWrapper)).isEqualTo(second);
  }

  private static RedisCommand<String, String, String> command() {
    RedisCommand<String, String, String> command = new Command<>(CommandType.GET, null);
    LettuceCommandPeer.initialize(command);
    return command;
  }

  private static RedisCommand<?, ?, ?> commandWithPeer(SocketAddress address) {
    RedisCommand<?, ?, ?> command = command();
    LettuceCommandPeer.record(command, address);
    return command;
  }

  private static class SubclassAsyncCommand extends AsyncCommand<String, String, String> {
    private SubclassAsyncCommand(RedisCommand<String, String, String> delegate) {
      super(delegate);
    }
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
