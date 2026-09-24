/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static scala.collection.JavaConverters.asScalaBufferConverter;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import redis.RedisClientActorLike;
import redis.RedisClientMasterSlaves;
import redis.RedisClientPool;
import redis.RedisServer;
import redis.SentinelMonitored;
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
  void actorClientTargetIsReusedByCommandsAndTransactions() {
    RedisClientActorLike client = mock(RedisClientActorLike.class);
    when(client.host()).thenReturn("host");
    when(client.port()).thenReturn(6380);

    RediscalaServerTargets.captureClientTarget(client);

    RedisServerTarget firstCommandTarget = RediscalaServerTargets.get(client);
    RedisServerTarget secondCommandTarget = RediscalaServerTargets.get(client);
    RedisServerTarget transactionTarget = RediscalaServerTargets.get(client);

    assertThat(secondCommandTarget).isSameAs(firstCommandTarget);
    assertThat(transactionTarget).isSameAs(firstCommandTarget);
    verify(client).host();
    verify(client).port();
  }

  @Test
  void successfulReconnectRefreshesActorClientTarget() {
    RedisClientActorLike client = mock(RedisClientActorLike.class);
    when(client.host()).thenReturn("host");
    when(client.port()).thenReturn(6380);
    RediscalaServerTargets.captureClientTarget(client);
    RedisServerTarget initialTarget = RediscalaServerTargets.get(client);

    boolean configurationChanged =
        RediscalaServerTargets.clientConfigurationChanged(client, "other-host", 6381);
    assertThat(configurationChanged).isTrue();
    RediscalaServerTargets.updateClientTarget(client, "other-host", 6381);

    RedisServerTarget refreshedTarget = RediscalaServerTargets.get(client);
    assertThat(refreshedTarget).isNotSameAs(initialTarget);
    assertTarget(refreshedTarget, "other-host", 6381);
    assertThat(RediscalaServerTargets.get(client)).isSameAs(refreshedTarget);
  }

  @Test
  void unchangedReconnectReusesActorClientTarget() {
    RedisClientActorLike client = mock(RedisClientActorLike.class);
    when(client.host()).thenReturn("host");
    when(client.port()).thenReturn(6380);
    RediscalaServerTargets.captureClientTarget(client);
    RedisServerTarget initialTarget = RediscalaServerTargets.get(client);

    boolean configurationChanged =
        RediscalaServerTargets.clientConfigurationChanged(client, "host", 6380);

    assertThat(configurationChanged).isFalse();
    assertThat(RediscalaServerTargets.get(client)).isSameAs(initialTarget);
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
  void mutablePoolStateRefreshesFromMap() {
    RedisServer first = server("node", 7000);
    RedisServer second = new RedisServer("node", 7000, Option.apply("password"), Option.apply(1));
    HashMap<RedisServer, Object> connections = new HashMap<>();
    connections.$plus$eq(new Tuple2<>(first, new Object()));
    connections.$plus$eq(new Tuple2<>(second, new Object()));

    RediscalaServerTargets.MutablePoolState state =
        RediscalaServerTargets.MutablePoolState.fromMap(connections);

    assertTarget(state.target(), "node:7000,node:7000", null);
    connections.remove(server("missing", 7001));
    state.refresh(connections);
    assertTarget(state.target(), "node:7000,node:7000", null);
    connections.remove(first);
    state.refresh(connections);
    assertTarget(state.target(), "node", 7000);
    connections.$plus$eq(new Tuple2<>(second, new Object()));
    state.refresh(connections);
    assertTarget(state.target(), "node", 7000);
    connections.$plus$eq(new Tuple2<>(first, new Object()));
    state.refresh(connections);
    assertTarget(state.target(), "node:7000,node:7000", null);
  }

  @Test
  void mutablePoolStateFailsClosedWhenUnavailable() {
    HashMap<Object, Object> connections = new HashMap<>();
    RedisServer server = server("node", 7000);
    connections.$plus$eq(new Tuple2<>(server, new Object()));

    RediscalaServerTargets.MutablePoolState state =
        RediscalaServerTargets.MutablePoolState.fromMap(connections);

    assertThat(state.target()).isNotNull();
    connections.$plus$eq(new Tuple2<>(new Object(), new Object()));
    state.refresh(connections);
    assertThat(state.target()).isNull();
    connections.clear();
    connections.$plus$eq(new Tuple2<>(server, new Object()));
    state.refresh(connections);
    assertTarget(state.target(), "node", 7000);
  }

  @Test
  void mutablePoolStateRefreshesAfterBeingMarkedUnavailable() {
    RedisServer server = server("node", 7000);
    HashMap<RedisServer, Object> connections = new HashMap<>();
    connections.$plus$eq(new Tuple2<>(server, new Object()));

    RediscalaServerTargets.MutablePoolState state =
        RediscalaServerTargets.MutablePoolState.fromMap(connections);

    state.markUnavailable();
    assertThat(state.target()).isNull();
    state.refresh(connections);
    assertTarget(state.target(), "node", 7000);
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
    SentinelMonitored client = mock(SentinelMonitored.class);
    when(client.master()).thenReturn("mymaster");
    when(client.sentinels())
        .thenReturn(sequence(new Tuple2<>("sentinel2", 26380), new Tuple2<>("sentinel1", 26379)));

    assertTarget(
        RediscalaServerTargets.of(client), "sentinel1:26379,sentinel2:26380/mymaster", null);
  }

  @Test
  @SuppressWarnings("MockitoDoSetup")
  void sentinelsFailClosedOnUnsupportedMember() {
    SentinelMonitored client = mock(SentinelMonitored.class);
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
