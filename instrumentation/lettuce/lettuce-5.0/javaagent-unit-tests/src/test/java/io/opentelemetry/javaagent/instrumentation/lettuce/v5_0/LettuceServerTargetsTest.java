/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisURI;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;

class LettuceServerTargetsTest {

  @Test
  void standalone() {
    RedisServerTarget target = LettuceServerTargets.of(RedisURI.create("redis://host:6379"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void standaloneDropsCredentialsAndDatabase() {
    RedisServerTarget target =
        LettuceServerTargets.of(RedisURI.create("redis://user:password@host:6379/2"));

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void sentinelsAreScopedByTheirMaster() {
    RedisServerTarget target =
        LettuceServerTargets.of(
            RedisURI.Builder.sentinel("sentinel2", 26380, "mymaster")
                .withSentinel("sentinel1", 26379)
                .withSentinel("sentinel2", 26380)
                .build());

    assertThat(target.getAddress())
        .isEqualTo("sentinel2:26380,sentinel1:26379,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelWithoutMasterKeepsTheSentinelEndpoints() {
    RedisURI redisUri = RedisURI.Builder.sentinel("sentinel1", 26379, "mymaster").build();
    redisUri.getSentinels().add(RedisURI.create("redis://sentinel2:26380"));
    redisUri.setSentinelMasterId(null);

    RedisServerTarget target = LettuceServerTargets.of(redisUri);

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void masterSlaveSentinelIterableKeepsTheMasterSuffix() {
    RedisURI sentinel =
        RedisURI.Builder.sentinel("sentinel2", 26380, "mymaster")
            .withSentinel("sentinel1", 26379)
            .build();

    RedisServerTarget target = LettuceServerTargets.ofMasterSlaveUris(singletonList(sentinel));

    assertThat(target.getAddress()).isEqualTo("sentinel2:26380,sentinel1:26379/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void socket() {
    RedisServerTarget target =
        LettuceServerTargets.of(RedisURI.Builder.socket("/var/run/redis.sock").build());

    assertThat(target.getAddress()).isEqualTo("/var/run/redis.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void laterUriChangesDoNotChangeTheTarget() {
    RedisURI redisUri = RedisURI.create("redis://host:6379");
    RedisServerTarget target = LettuceServerTargets.of(redisUri);

    redisUri.setHost("other");
    redisUri.setPort(6380);

    assertThat(target.getAddress()).isEqualTo("host");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void noUri() {
    assertThat(LettuceServerTargets.of(null)).isNull();
  }

  @Test
  void clusterKeepsConfiguredEndpointOrder() {
    RedisServerTarget first =
        LettuceServerTargets.ofUris(
            asList(RedisURI.create("redis://node2:7001"), RedisURI.create("redis://node1:7000")));
    RedisServerTarget second =
        LettuceServerTargets.ofUris(
            asList(RedisURI.create("redis://node1:7000"), RedisURI.create("redis://node2:7001")));

    assertThat(first.getAddress()).isEqualTo("node2:7001,node1:7000");
    assertThat(second.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(first.getPort()).isNull();
    assertThat(second.getPort()).isNull();
  }

  @Test
  void clusterKeepsDuplicatesFromIterable() {
    Iterable<RedisURI> redisUris =
        () ->
            asList(
                    RedisURI.create("redis://node2:7001"),
                    RedisURI.create("redis://node1:7000"),
                    RedisURI.create("redis://node2:7001"))
                .iterator();

    RedisServerTarget target = LettuceServerTargets.ofUris(redisUris);

    assertThat(target.getAddress()).isEqualTo("node2:7001,node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterWithOneEndpointKeepsItsPort() {
    RedisServerTarget target =
        LettuceServerTargets.ofUris(singletonList(RedisURI.create("redis://node1:7000")));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void clusterDropsCredentialsAndDatabase() {
    RedisServerTarget target =
        LettuceServerTargets.ofUris(
            asList(
                RedisURI.create("redis://user:pass@node1:7000/2"),
                RedisURI.create("redis://node2:7001")));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterKeepsUnixSockets() {
    RedisServerTarget target =
        LettuceServerTargets.ofUris(
            asList(
                RedisURI.Builder.socket("/var/run/redis2.sock").build(),
                RedisURI.Builder.socket("/var/run/redis1.sock").build()));

    assertThat(target.getAddress()).isEqualTo("/var/run/redis2.sock,/var/run/redis1.sock");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterKeepsIpv6Ports() {
    RedisServerTarget target =
        LettuceServerTargets.ofUris(
            asList(RedisURI.create("redis://[::2]:7001"), RedisURI.create("redis://[::1]:7000")));

    assertThat(target.getAddress()).isEqualTo("[::2]:7001,[::1]:7000");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void laterClusterUriChangesDoNotChangeTheTarget() {
    RedisURI first = RedisURI.create("redis://node1:7000");
    RedisURI second = RedisURI.create("redis://node2:7001");
    RedisServerTarget target = LettuceServerTargets.ofUris(asList(first, second));

    first.setHost("other");
    second.setPort(7002);

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noClusterUris() {
    assertThat(LettuceServerTargets.ofUris(null)).isNull();
    assertThat(LettuceServerTargets.ofUris(emptyList())).isNull();
  }
}
