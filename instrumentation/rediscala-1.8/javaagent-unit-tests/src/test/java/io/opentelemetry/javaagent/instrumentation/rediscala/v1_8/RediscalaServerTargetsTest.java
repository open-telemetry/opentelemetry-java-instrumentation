/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static scala.collection.JavaConverters.asScalaBufferConverter;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import redis.RedisClientActorLike;
import redis.RedisClientMasterSlaves;
import redis.RedisClientPool;
import redis.RedisServer;
import redis.SentinelMonitoredRedisClient;
import scala.Option;
import scala.Tuple2;
import scala.collection.Seq;
import scala.collection.mutable.HashMap;

class RediscalaServerTargetsTest {

  @Test
  void actorClient() {
    RedisClientActorLike client = mock(RedisClientActorLike.class);
    when(client.host()).thenReturn("host");
    when(client.port()).thenReturn(6380);

    assertTarget(RediscalaServerTargets.of(client), "host", 6380);
  }

  @Test
  void masterAndSlavesKeepMasterFirstAndSortReplicas() {
    RedisClientMasterSlaves client = mock(RedisClientMasterSlaves.class);
    when(client.master()).thenReturn(server("master", 6379));
    when(client.slaves()).thenReturn(sequence(server("replica2", 6381), server("replica1", 6380)));

    assertTarget(
        RediscalaServerTargets.of(client), "master:6379,replica1:6380,replica2:6381", null);
  }

  @Test
  @SuppressWarnings("MockitoDoSetup")
  void masterAndSlavesFailClosedOnUnsupportedReplica() {
    RedisClientMasterSlaves client = mock(RedisClientMasterSlaves.class);
    when(client.master()).thenReturn(server("master", 6379));
    doReturn(sequence(server("replica", 6380), new Object())).when(client).slaves();

    assertThat(RediscalaServerTargets.of(client)).isNull();
  }

  @Test
  void poolSortsConfiguredServersAndKeepsDuplicates() {
    RedisClientPool client = mock(RedisClientPool.class);
    when(client.redisServers())
        .thenReturn(sequence(server("node2", 7001), server("node1", 7000), server("node2", 7001)));

    assertTarget(RediscalaServerTargets.of(client), "node1:7000,node2:7001,node2:7001", null);
  }

  @Test
  void poolWithOneServerKeepsItsPort() {
    RedisClientPool client = mock(RedisClientPool.class);
    when(client.redisServers()).thenReturn(sequence(server("node1", 7000)));

    assertTarget(RediscalaServerTargets.of(client), "node1", 7000);
  }

  @Test
  void emptyPoolIsOmitted() {
    RedisClientPool client = mock(RedisClientPool.class);
    when(client.redisServers()).thenReturn(sequence());

    assertThat(RediscalaServerTargets.of(client)).isNull();
  }

  @Test
  void mutablePoolStatePreservesEndpointMultiplicity() {
    RedisServer first = server("node", 7000);
    RedisServer second = new RedisServer("node", 7000, Option.apply("password"), Option.apply(1));
    HashMap<RedisServer, Object> connections = new HashMap<>();
    connections.$plus$eq(new Tuple2<>(first, new Object()));
    connections.$plus$eq(new Tuple2<>(second, new Object()));

    RediscalaServerTargets.MutablePoolState state =
        RediscalaServerTargets.MutablePoolState.fromMap(connections);

    assertTarget(state.target(), "node:7000,node:7000", null);
    state.remove(RediscalaServerTargets.endpoint(first));
    assertTarget(state.target(), "node", 7000);
    state.add(RediscalaServerTargets.endpoint(second));
    assertTarget(state.target(), "node:7000,node:7000", null);
  }

  @Test
  void mutablePoolStateFailsClosedWhenUnavailable() {
    HashMap<Object, Object> connections = new HashMap<>();
    connections.$plus$eq(new Tuple2<>(new Object(), new Object()));

    RediscalaServerTargets.MutablePoolState state =
        RediscalaServerTargets.MutablePoolState.fromMap(connections);

    assertThat(state.target()).isNull();
    assertThat(state.isAvailable()).isFalse();
  }

  @Test
  @SuppressWarnings("MockitoDoSetup")
  void poolFailsClosedOnUnsupportedServer() {
    RedisClientPool client = mock(RedisClientPool.class);
    doReturn(sequence(server("node1", 7000), new Object())).when(client).redisServers();

    assertThat(RediscalaServerTargets.of(client)).isNull();
  }

  @Test
  void sentinelsAreScopedByTheirMaster() {
    SentinelMonitoredRedisClient client = mock(SentinelMonitoredRedisClient.class);
    when(client.master()).thenReturn("mymaster");
    when(client.sentinels())
        .thenReturn(sequence(new Tuple2<>("sentinel2", 26380), new Tuple2<>("sentinel1", 26379)));

    assertTarget(
        RediscalaServerTargets.of(client), "sentinel1:26379,sentinel2:26380/mymaster", null);
  }

  @Test
  @SuppressWarnings("MockitoDoSetup")
  void sentinelsFailClosedOnUnsupportedMember() {
    SentinelMonitoredRedisClient client = mock(SentinelMonitoredRedisClient.class);
    when(client.master()).thenReturn("mymaster");
    doReturn(sequence(new Tuple2<>("sentinel1", 26379), new Object())).when(client).sentinels();

    assertThat(RediscalaServerTargets.of(client)).isNull();
  }

  @Test
  void noClient() {
    assertThat(RediscalaServerTargets.of(null)).isNull();
  }

  private static RedisServer server(String host, int port) {
    return new RedisServer(host, port, Option.empty(), Option.empty());
  }

  @SafeVarargs
  @SuppressWarnings("varargs")
  private static <T> Seq<T> sequence(T... values) {
    return asScalaBufferConverter(asList(values)).asScala().toSeq();
  }

  private static void assertTarget(
      RedisServerTarget target, String expectedAddress, Integer expectedPort) {
    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isEqualTo(expectedPort);
  }
}
