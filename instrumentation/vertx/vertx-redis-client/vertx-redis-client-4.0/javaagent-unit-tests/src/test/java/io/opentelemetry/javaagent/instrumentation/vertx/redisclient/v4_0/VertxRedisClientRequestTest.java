/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.redis.client.impl.RedisStandaloneConnection;
import io.vertx.redis.client.impl.RedisURI;
import org.junit.jupiter.api.Test;

class VertxRedisClientRequestTest {

  private static final String SELECTED_HOST = "selected-node";
  private static final int SELECTED_PORT = 6379;
  private static final String PEER_HOST = "127.0.0.1";
  private static final int PEER_PORT = 6380;

  @Test
  void requestWithoutTargetUsesSelectedEndpointOnlyForLegacySemconv() {
    VertxRedisClientRequest request = request(null);

    assertThat(request.getServerAddress())
        .isEqualTo(emitStableDatabaseSemconv() ? null : SELECTED_HOST);
    assertThat(request.getServerPort())
        .isEqualTo(emitStableDatabaseSemconv() ? null : SELECTED_PORT);
    assertThat(request.getPeerAddress()).isEqualTo(PEER_HOST);
    assertThat(request.getPeerPort()).isEqualTo(PEER_PORT);
  }

  @Test
  void requestWithTargetUsesConfiguredTargetOnlyForStableSemconv() {
    VertxRedisClientRequest request =
        request(RedisServerTarget.ofHostAndPort("configured-node", 6381));

    assertThat(request.getServerAddress())
        .isEqualTo(emitStableDatabaseSemconv() ? "configured-node" : SELECTED_HOST);
    assertThat(request.getServerPort())
        .isEqualTo(emitStableDatabaseSemconv() ? 6381 : SELECTED_PORT);
    assertThat(request.getPeerAddress()).isEqualTo(PEER_HOST);
    assertThat(request.getPeerPort()).isEqualTo(PEER_PORT);
  }

  private static VertxRedisClientRequest request(RedisServerTarget target) {
    RedisURI redisUri = new RedisURI("redis://" + SELECTED_HOST + ":" + SELECTED_PORT);
    VertxRedisServerTargets.set(redisUri, target);

    NetSocket netSocket = mock(NetSocket.class);
    when(netSocket.remoteAddress())
        .thenReturn(SocketAddress.inetSocketAddress(PEER_PORT, PEER_HOST));

    return new VertxRedisClientRequest(
        "GET", emptyList(), redisUri, mock(RedisStandaloneConnection.class), netSocket);
  }
}
