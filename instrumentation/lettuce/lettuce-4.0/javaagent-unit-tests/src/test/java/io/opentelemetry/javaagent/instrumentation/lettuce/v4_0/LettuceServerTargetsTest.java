/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;

class LettuceServerTargetsTest {

  @Test
  void standaloneNetworkTargets() {
    RedisServerTarget service =
        LettuceServerTargets.of(RedisURI.create("redis://cache.service.consul:6379/2"));
    RedisServerTarget ipv4 = LettuceServerTargets.of(RedisURI.create("redis://192.0.2.1:6380"));
    RedisServerTarget ipv6 = LettuceServerTargets.of(RedisURI.create("redis://[::1]:6381"));

    assertThat(service.getAddress()).isEqualTo("cache.service.consul");
    assertThat(service.getPort()).isNull();
    assertThat(ipv4.getAddress()).isEqualTo("192.0.2.1");
    assertThat(ipv4.getPort()).isEqualTo(6380);
    assertThat(ipv6.getAddress()).isEqualTo("::1");
    assertThat(ipv6.getPort()).isEqualTo(6381);
  }

  @Test
  void unixSocketTarget() {
    RedisServerTarget target =
        LettuceServerTargets.of(RedisURI.Builder.socket("/var/run/redis.sock").build());

    assertThat(target.getAddress()).isEqualTo("/var/run/redis.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelTargetIncludesMasterAndPreservesDuplicates() {
    RedisServerTarget target =
        LettuceServerTargets.of(
            RedisURI.Builder.sentinel("sentinel2", 26380, "mymaster")
                .withSentinel("sentinel1", 26379)
                .withSentinel("sentinel2", 26380)
                .build());

    assertThat(target.getAddress())
        .isEqualTo("sentinel1:26379,sentinel2:26380,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void masterReplicaSentinelIterableKeepsMasterSuffix() {
    RedisURI sentinel =
        RedisURI.Builder.sentinel("sentinel2", 26380, "mymaster")
            .withSentinel("sentinel1", 26379)
            .build();

    RedisServerTarget target = LettuceServerTargets.ofMasterSlaveUris(singletonList(sentinel));

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void masterIdWithoutSentinelsUsesTheUriItself() {
    RedisURI master = RedisURI.create("redis://cache.service.consul:6379");
    master.setSentinelMasterId("mymaster");
    RedisURI replica = RedisURI.create("redis://replica:7001");

    RedisServerTarget target = LettuceServerTargets.of(master);
    RedisServerTarget masterReplica =
        LettuceServerTargets.ofMasterSlaveUris(asList(master, replica));

    assertThat(target.getAddress()).isEqualTo("cache.service.consul");
    assertThat(target.getPort()).isNull();
    assertThat(masterReplica.getAddress()).isEqualTo("cache.service.consul:6379,replica:7001");
    assertThat(masterReplica.getPort()).isNull();
  }

  @Test
  void orderedTargetsPreserveOrderAndDuplicates() {
    RedisServerTarget target =
        LettuceServerTargets.ofUris(
            asList(
                RedisURI.create("redis://node2:7001"),
                RedisURI.create("redis://node1:7000"),
                RedisURI.create("redis://node2:7001")));

    assertThat(target.getAddress()).isEqualTo("node2:7001,node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void invalidMemberFailsClosed() {
    RedisURI invalid = RedisURI.create("redis://node2:7001");
    invalid.setHost("invalid,host");

    assertThat(LettuceServerTargets.ofUris(asList(RedisURI.create("redis://node1:7000"), invalid)))
        .isNull();
    assertThat(
            LettuceServerTargets.ofUris(
                asList(RedisURI.create("redis://node1:7000"), "unsupported")))
        .isNull();
  }

  @Test
  void mutableUrisAreSnapshotted() {
    RedisURI first = RedisURI.create("redis://node1:7000");
    RedisURI second = RedisURI.create("redis://node2:7001");
    RedisServerTarget target = LettuceServerTargets.ofUris(asList(first, second));

    first.setHost("other");
    second.setPort(7002);

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void multipleUnixSocketsFailClosed() {
    assertThat(
            LettuceServerTargets.ofUris(
                asList(
                    RedisURI.Builder.socket("/var/run/redis1.sock").build(),
                    RedisURI.Builder.socket("/var/run/redis2.sock").build())))
        .isNull();
  }
}
