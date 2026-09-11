/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;

class LettuceServerTargetTest {

  @Test
  void standaloneNetworkTargets() {
    RedisServerTarget service =
        LettuceServerTarget.of(RedisURI.create("redis://cache.service.consul:6379/2"));
    RedisServerTarget ipv4 = LettuceServerTarget.of(RedisURI.create("redis://192.0.2.1:6380"));
    RedisServerTarget ipv6 = LettuceServerTarget.of(RedisURI.create("redis://[::1]:6381"));

    assertThat(service).isNotNull();
    assertThat(ipv4).isNotNull();
    assertThat(ipv6).isNotNull();
    assertThat(service.getAddress()).isEqualTo("cache.service.consul");
    assertThat(service.getPort()).isNull();
    assertThat(ipv4.getAddress()).isEqualTo("192.0.2.1");
    assertThat(ipv4.getPort()).isEqualTo(6380);
    assertThat(ipv6.getAddress()).isEqualTo("::1");
    assertThat(ipv6.getPort()).isEqualTo(6381);
  }

  @Test
  void noUri() {
    assertThat(LettuceServerTarget.of(null)).isNull();
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

  @Test
  void blankOrUnsafeSentinelMasterNameDropsSuffixButKeepsSentinels() {
    RedisURI blankMaster =
        RedisURI.Builder.sentinel("sentinel2", 26380).withSentinel("sentinel1", 26379).build();
    blankMaster.setSentinelMasterId("  ");
    RedisURI unsafeMaster =
        RedisURI.Builder.sentinel("sentinel2", 26380).withSentinel("sentinel1", 26379).build();
    unsafeMaster.setSentinelMasterId("master,name");

    RedisServerTarget blankTarget = LettuceServerTarget.of(blankMaster);
    RedisServerTarget unsafeTarget = LettuceServerTarget.of(unsafeMaster);

    assertThat(blankTarget).isNotNull();
    assertThat(unsafeTarget).isNotNull();
    assertThat(blankTarget.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(blankTarget.getPort()).isNull();
    assertThat(unsafeTarget.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(unsafeTarget.getPort()).isNull();
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

  @Test
  void invalidMemberFailsClosed() {
    RedisURI invalid = RedisURI.create("redis://node2:7001");
    invalid.setHost("invalid,host");

    assertThat(LettuceServerTarget.ofUris(asList(RedisURI.create("redis://node1:7000"), invalid)))
        .isNull();
    assertThat(
            LettuceServerTarget.ofUris(
                asList(RedisURI.create("redis://node1:7000"), "unsupported")))
        .isNull();
    assertThat(LettuceServerTarget.ofUris(asList(RedisURI.create("redis://node1:7000"), null)))
        .isNull();
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

  @Test
  void noClusterUris() {
    assertThat(LettuceServerTarget.ofUris(null)).isNull();
    assertThat(LettuceServerTarget.ofUris(emptyList())).isNull();
  }
}
