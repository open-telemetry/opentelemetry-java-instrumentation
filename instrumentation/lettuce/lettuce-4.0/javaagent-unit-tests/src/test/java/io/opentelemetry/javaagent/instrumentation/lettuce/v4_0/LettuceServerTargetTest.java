/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class LettuceServerTargetTest {

  @ParameterizedTest
  @MethodSource("standaloneNetworkTargets")
  void standaloneNetworkTarget(RedisURI redisUri, String expectedAddress, Integer expectedPort) {
    RedisServerTarget target = LettuceServerTarget.of(redisUri);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isEqualTo(expectedPort);
  }

  private static Stream<Arguments> standaloneNetworkTargets() {
    return Stream.of(
        argumentSet(
            "service address omits the default port",
            RedisURI.create("redis://cache.service.consul:6379/2"),
            "cache.service.consul",
            null),
        argumentSet(
            "IPv4 address keeps a non-default port",
            RedisURI.create("redis://192.0.2.1:6380"),
            "192.0.2.1",
            6380),
        argumentSet(
            "IPv6 address loses brackets", RedisURI.create("redis://[::1]:6381"), "::1", 6381));
  }

  @Test
  void unixSocketTarget() {
    RedisServerTarget target =
        LettuceServerTarget.of(RedisURI.Builder.socket("/var/run/redis.sock").build());

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("/var/run/redis.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelTargetIncludesMasterAndPreservesDuplicates() {
    RedisServerTarget target =
        LettuceServerTarget.of(
            RedisURI.Builder.sentinel("sentinel2", 26380, "mymaster")
                .withSentinel("sentinel1", 26379)
                .withSentinel("sentinel2", 26380)
                .build());

    assertThat(target).isNotNull();
    assertThat(target.getAddress())
        .isEqualTo("sentinel1:26379,sentinel2:26380,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"  ", "master,name"})
  void invalidSentinelMasterNameDropsSuffixButKeepsSentinels(String masterName) {
    RedisURI redisUri =
        RedisURI.Builder.sentinel("sentinel2", 26380).withSentinel("sentinel1", 26379).build();
    redisUri.setSentinelMasterId(masterName);

    RedisServerTarget target = LettuceServerTarget.of(redisUri);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void masterReplicaSentinelIterableKeepsMasterSuffix() {
    RedisURI sentinel =
        RedisURI.Builder.sentinel("sentinel2", 26380, "mymaster")
            .withSentinel("sentinel1", 26379)
            .build();

    RedisServerTarget target = LettuceServerTarget.ofMasterSlaveUris(singletonList(sentinel));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void masterIdWithoutSentinelsUsesTheUriItself() {
    RedisURI master = RedisURI.create("redis://cache.service.consul:6379");
    master.setSentinelMasterId("mymaster");
    RedisURI replica = RedisURI.create("redis://replica:7001");

    RedisServerTarget target = LettuceServerTarget.of(master);
    RedisServerTarget masterReplica =
        LettuceServerTarget.ofMasterSlaveUris(asList(master, replica));

    assertThat(target).isNotNull();
    assertThat(masterReplica).isNotNull();
    assertThat(target.getAddress()).isEqualTo("cache.service.consul");
    assertThat(target.getPort()).isNull();
    assertThat(masterReplica.getAddress()).isEqualTo("cache.service.consul:6379,replica:7001");
    assertThat(masterReplica.getPort()).isNull();
  }

  @Test
  void orderedTargetsPreserveOrderAndDuplicates() {
    RedisServerTarget target =
        LettuceServerTarget.ofUris(
            asList(
                RedisURI.create("redis://node2:7001"),
                RedisURI.create("redis://node1:7000"),
                RedisURI.create("redis://node2:7001")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node2:7001,node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @ParameterizedTest
  @MethodSource("invalidUriLists")
  void invalidUriListHasNoTarget(Iterable<?> redisUris) {
    assertThat(LettuceServerTarget.ofUris(redisUris)).isNull();
  }

  private static Stream<Arguments> invalidUriLists() {
    RedisURI invalid = RedisURI.create("redis://node2:7001");
    invalid.setHost("invalid,host");

    return Stream.of(
        argumentSet("null list", (Object) null),
        argumentSet("empty list", emptyList()),
        argumentSet("unsafe host", asList(RedisURI.create("redis://node1:7000"), invalid)),
        argumentSet(
            "unsupported member", asList(RedisURI.create("redis://node1:7000"), "unsupported")),
        argumentSet("null member", asList(RedisURI.create("redis://node1:7000"), null)));
  }

  @Test
  void mutableUrisAreSnapshotted() {
    RedisURI first = RedisURI.create("redis://node1:7000");
    RedisURI second = RedisURI.create("redis://node2:7001");
    RedisServerTarget target = LettuceServerTarget.ofUris(asList(first, second));

    first.setHost("other");
    second.setPort(7002);

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void multipleUnixSocketsFailClosed() {
    assertThat(
            LettuceServerTarget.ofUris(
                asList(
                    RedisURI.Builder.socket("/var/run/redis1.sock").build(),
                    RedisURI.Builder.socket("/var/run/redis2.sock").build())))
        .isNull();
  }

  @Test
  void singleMemberClusterKeepsItsPort() {
    RedisServerTarget target =
        LettuceServerTarget.ofUris(singletonList(RedisURI.create("redis://node1:7000")));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void singleUnixSocketMemberClusterKeepsItsPath() {
    RedisServerTarget target =
        LettuceServerTarget.ofUris(
            singletonList(RedisURI.Builder.socket("/var/run/redis1.sock").build()));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("/var/run/redis1.sock");
    assertThat(target.getPort()).isNull();
  }
}
