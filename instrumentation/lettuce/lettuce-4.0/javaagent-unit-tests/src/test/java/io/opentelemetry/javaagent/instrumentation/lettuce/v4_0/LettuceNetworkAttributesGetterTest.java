/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.unix.DomainSocketAddress;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
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
    RedisCommand<?, ?, ?> command = commandWithPeer(address);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(LettuceSingletons.commandPeerAddress(command)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? expectedAddress : null);
    assertThat(getter.getNetworkPeerPort(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void commandDropsUnresolvedSelectedAddress() {
    RedisCommand<?, ?, ?> command =
        commandWithPeer(InetSocketAddress.createUnresolved("redis.example", PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void commandRetryUsesLastSelectedAddress() throws UnknownHostException {
    RedisCommand<?, ?, ?> command = command();
    LettuceCommandPeer peerAddress = new LettuceCommandPeer();
    LettuceSingletons.COMMAND_PEER.set(command, peerAddress);
    LettuceSingletons.recordCommandPeers(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT));
    LettuceSingletons.recordCommandPeers(
        command, new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 4}), PORT));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.4" : null);
    assertThat(getter.getNetworkPeerPort(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void commandUsesDomainSocketPath() {
    DomainSocketAddress address = new DomainSocketAddress("/var/run/redis.sock");
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.COMMAND_PEER.set(command, new LettuceCommandPeer());
    Channel channel = mock(Channel.class);
    when(channel.remoteAddress()).thenReturn(address);
    ChannelHandlerContext context = mock(ChannelHandlerContext.class);
    when(context.channel()).thenReturn(channel);

    LettuceCommandHandlerInstrumentation.WriteAdvice.onEnter(context, command);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
    assertThat(LettuceSingletons.commandPeerAddress(command)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "/var/run/redis.sock" : null);
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void commandThatDoesNotExpectResponseDropsSelectedAddress() throws UnknownHostException {
    RedisCommand<?, ?, ?> command = new Command<>(CommandType.DEBUG, null);
    LettuceCommandPeer peer = new LettuceCommandPeer();
    peer.record(new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT));
    LettuceSingletons.COMMAND_PEER.set(command, peer);

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
    LettuceSingletons.recordCommandPeers(firstCommand, finalAddress);
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
    LettuceSingletons.recordCommandPeers(firstCommand, secondAddress);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(asList(firstCommand, secondCommand), null, null, null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  private static RedisCommand<?, ?, ?> command() {
    return new Command<>(CommandType.GET, null);
  }

  private static RedisCommand<?, ?, ?> commandWithPeer(SocketAddress address) {
    RedisCommand<?, ?, ?> command = command();
    LettuceCommandPeer peer = new LettuceCommandPeer();
    peer.record(address);
    LettuceSingletons.COMMAND_PEER.set(command, peer);
    return command;
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
}
