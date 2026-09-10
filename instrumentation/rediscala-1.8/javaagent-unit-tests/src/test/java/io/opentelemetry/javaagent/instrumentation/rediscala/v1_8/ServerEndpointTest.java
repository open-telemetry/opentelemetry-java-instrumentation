/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import redis.RedisClient;
import redis.RedisClientMasterSlaves;
import redis.SentinelMonitoredRedisClientMasterSlaves;
import scala.Option;

class ServerEndpointTest {

  @Test
  void extractsMasterSlavesMasterClientOnlyWhenRequested() {
    RedisClient masterClient = masterClient();
    RedisClientMasterSlaves client = mock(RedisClientMasterSlaves.class);
    when(client.masterClient()).thenReturn(masterClient);

    assertEndpoint(ServerEndpoint.create(client, true));
    assertThat(ServerEndpoint.create(client, false)).isNull();
  }

  @Test
  void extractsSentinelMasterSlavesMasterClientOnlyWhenRequested() {
    RedisClient masterClient = masterClient();
    SentinelMonitoredRedisClientMasterSlaves client =
        mock(SentinelMonitoredRedisClientMasterSlaves.class);
    when(client.masterClient()).thenReturn(masterClient);

    assertEndpoint(ServerEndpoint.create(client, true));
    assertThat(ServerEndpoint.create(client, false)).isNull();
  }

  private static RedisClient masterClient() {
    RedisClient client = mock(RedisClient.class);
    when(client.host()).thenReturn("master");
    when(client.port()).thenReturn(6380);
    when(client.db()).thenReturn(Option.apply(2));
    return client;
  }

  private static void assertEndpoint(ServerEndpoint endpoint) {
    assertThat(endpoint).isNotNull();
    assertThat(endpoint.getHost()).isEqualTo("master");
    assertThat(endpoint.getPort()).isEqualTo(6380);
    assertThat(endpoint.getDatabaseIndex()).isEqualTo(2);
  }
}
