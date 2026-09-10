/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import redis.clients.jedis.JedisShardInfo;

class JedisServerTargetsTest {

  @Test
  void shardsKeepTheirOrder() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(
            asList(new JedisShardInfo("shard2", 6380), new JedisShardInfo("shard1", 6379)));

    assertThat(target.getAddress()).isEqualTo("shard2:6380,shard1:6379");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void shardsWithSharedNonDefaultPortKeepPortsInline() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(
            asList(new JedisShardInfo("shard1", 6380), new JedisShardInfo("shard2", 6380)));

    assertThat(target.getAddress()).isEqualTo("shard1:6380,shard2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void oneShardOmitsItsDefaultPort() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(singletonList(new JedisShardInfo("shard1", 6379)));

    assertThat(target.getAddress()).isEqualTo("shard1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void duplicateShardsArePreserved() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(
            asList(new JedisShardInfo("shard1", 6379), new JedisShardInfo("shard1", 6379)));

    assertThat(target.getAddress()).isEqualTo("shard1,shard1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noShards() {
    assertThat(JedisServerTargets.ofShards(null)).isNull();
    assertThat(JedisServerTargets.ofShards(emptyList())).isNull();
  }

  @Test
  void sentinelsShareTheirMasterSuffix() {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels("mymaster", sentinels("sentinel2:26380", "sentinel1:26379"));

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"2001:db8::1", "2001:db8::"})
  void stringIpv6SentinelKeepsItsPort(String host) {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels("mymaster", sentinels(host + ":26379"));

    assertThat(target.getAddress()).isEqualTo("[" + host + "]:26379/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelsWithoutAMasterNameKeepTheSentinels() {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels(" ", sentinels("sentinel1:26379", "sentinel2:26380"));

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379,sentinel2:26380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noSentinels() {
    assertThat(JedisServerTargets.ofSentinels(null, null)).isNull();
    assertThat(JedisServerTargets.ofSentinels(null, sentinels())).isNull();
  }

  @Test
  void sentinelListWithNullMemberFailsClosed() {
    assertThat(
            JedisServerTargets.ofSentinels(
                "mymaster", new LinkedHashSet<>(asList("sentinel1:26379", null))))
        .isNull();
  }

  @Test
  void nodesAreSorted() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(new LinkedHashSet<>(asList("node2:7001", "node1:7000")));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void nodesIncludeAtMostFirstFiveAfterSorting() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(
            asList(
                "node6:6379",
                "node5:6379",
                "node4:6379",
                "node3:6379",
                "node2:6379",
                "node1:6379"));

    assertThat(target.getAddress()).isEqualTo("node1,node2,node3,node4,node5");
  }

  @Test
  void oneNodeKeepsItsPort() {
    RedisServerTarget target = JedisServerTargets.ofNodes(singletonList("node1:7000"));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void ipv6NodeKeepsItsPort() {
    RedisServerTarget target = JedisServerTargets.ofNodes(singletonList("2001:db8::1:7000"));

    assertThat(target.getAddress()).isEqualTo("2001:db8::1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void ipv6NodeWithoutPortRemainsUnchanged() {
    RedisServerTarget target = JedisServerTargets.ofNodes(singletonList("2001:db8::1"));

    assertThat(target.getAddress()).isEqualTo("2001:db8::1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noNodes() {
    assertThat(JedisServerTargets.ofNodes(null)).isNull();
    assertThat(JedisServerTargets.ofNodes(emptyList())).isNull();
  }

  @Test
  void nodeListWithNullMemberFailsClosed() {
    assertThat(JedisServerTargets.ofNodes(asList("node1:7000", null))).isNull();
  }

  @Test
  void nodeListWithUnsupportedMemberFailsClosedWithoutConversion() {
    assertThat(JedisServerTargets.ofNodes(asList("node1:7000", unconvertibleMember()))).isNull();
  }

  private static Object unconvertibleMember() {
    return new Object() {
      @Override
      public String toString() {
        throw new IllegalStateException("must not convert unsupported member");
      }
    };
  }

  private static Set<String> sentinels(String... addresses) {
    return new LinkedHashSet<>(asList(addresses));
  }
}
