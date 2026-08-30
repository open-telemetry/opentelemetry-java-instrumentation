/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.lambdaworks.redis.RedisChannelHandler;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class LettuceNetworkAttributesGetterTest {

  private static final int PORT = 6379;

  @Test
  void commandUsesResolvedSelectedAddress() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.COMMAND_PEER.set(command, new LettucePeerAddress(address));

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(LettuceSingletons.commandPeerAddress(command)).isEqualTo(address);
    assertThat(getter.getNetworkPeerAddress(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? "10.1.2.3" : null);
    assertThat(getter.getNetworkPeerPort(command, null))
        .isEqualTo(emitStableDatabaseSemconv() ? PORT : null);
  }

  @Test
  void commandDropsUnresolvedSelectedAddress() {
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.COMMAND_PEER.set(
        command, new LettucePeerAddress(InetSocketAddress.createUnresolved("redis.example", PORT)));

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
    assertThat(getter.getServerPort(command)).isEqualTo(PORT);
    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  @Test
  void batchUsesResolvedSelectedAddress() throws UnknownHostException {
    InetSocketAddress address =
        new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT);
    LettuceBatchRequest request =
        LettuceBatchRequest.create(
            singletonList(command()), null, new LettucePeerAddress(address), null, null);

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
            singletonList(command()),
            null,
            new LettucePeerAddress(InetSocketAddress.createUnresolved("redis.example", PORT)),
            null,
            null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void disconnectDropsPeerOfBatchFlushedBeforeIt() throws UnknownHostException {
    RedisChannelHandler<?, ?> connection = mock(RedisChannelHandler.class);
    LettuceSingletons.CONNECTION_PEER.set(
        connection,
        new LettucePeerAddress(
            new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT)));
    LettuceBatchRequest request =
        LettuceBatchRequest.create(
            singletonList(command()),
            null,
            LettuceSingletons.CONNECTION_PEER.get(connection),
            null,
            null);

    LettuceSingletons.clearConnectionPeer(connection);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(request, null)).isNull();
    assertThat(getter.getNetworkPeerPort(request, null)).isNull();
  }

  @Test
  void disconnectDropsPeerAndKeepsConfiguredServerAddress() throws UnknownHostException {
    RedisChannelHandler<?, ?> connection = mock(RedisChannelHandler.class);
    InetSocketAddress configuredAddress = InetSocketAddress.createUnresolved("redis.example", PORT);
    LettuceSingletons.CONNECTION_ADDRESS.set(connection, configuredAddress);
    LettuceSingletons.CONNECTION_PEER.set(
        connection,
        new LettucePeerAddress(
            new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT)));

    LettuceSingletons.clearConnectionPeer(connection);

    assertThat(LettuceSingletons.CONNECTION_PEER.get(connection)).isNull();
    assertThat(LettuceSingletons.CONNECTION_ADDRESS.get(connection)).isEqualTo(configuredAddress);
  }

  @Test
  void disconnectDropsPeerOfCommandDispatchedBeforeIt() throws UnknownHostException {
    RedisChannelHandler<?, ?> connection = mock(RedisChannelHandler.class);
    LettuceSingletons.CONNECTION_PEER.set(
        connection,
        new LettucePeerAddress(
            new InetSocketAddress(InetAddress.getByAddress(new byte[] {10, 1, 2, 3}), PORT)));
    RedisCommand<?, ?, ?> command = command();
    LettuceSingletons.COMMAND_PEER.set(command, LettuceSingletons.CONNECTION_PEER.get(connection));

    LettuceSingletons.clearConnectionPeer(connection);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getNetworkPeerAddress(command, null)).isNull();
    assertThat(getter.getNetworkPeerPort(command, null)).isNull();
  }

  private static RedisCommand<?, ?, ?> command() {
    return new Command<>(CommandType.GET, null);
  }
}
