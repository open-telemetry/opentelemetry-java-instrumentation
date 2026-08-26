/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.RedisCommand;
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

  private static RedisCommand<?, ?, ?> command() {
    return new Command<>(CommandType.GET, null);
  }
}
