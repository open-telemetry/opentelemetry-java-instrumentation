/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisShardInfo;

class JedisServerTargetsTest {

  @Test
  void shardsKeepEveryConfiguredEndpoint() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(
            asList(new JedisShardInfo("shard1", 6379), new JedisShardInfo("shard2", 6380)));

    assertThat(target.getAddress()).isEqualTo("shard1:6379,shard2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void oneShardKeepsItsPort() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(singletonList(new JedisShardInfo("shard1", 6379)));

    assertThat(target.getAddress()).isEqualTo("shard1");
    assertThat(target.getPort()).isEqualTo(6379);
  }

  @Test
  void noShards() {
    assertThat(JedisServerTargets.ofShards(null)).isNull();
    assertThat(JedisServerTargets.ofShards(emptyList())).isNull();
  }

  @Test
  void sentinelsAreScopedByTheirMaster() {
    RedisServerTarget target =
        JedisServerTargets.ofSentinels("mymaster", sentinels("sentinel1:26379"));

    assertThat(target.getAddress()).isEqualTo("sentinel1:26379/mymaster");
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
  void clusterNodesKeepEveryConfiguredEndpoint() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(
            asList(new HostAndPort("node1", 7000), new HostAndPort("node2", 7001)));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void oneClusterNodeKeepsItsPort() {
    RedisServerTarget target =
        JedisServerTargets.ofNodes(singletonList(new HostAndPort("node1", 7000)));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void noClusterNodes() {
    assertThat(JedisServerTargets.ofNodes(null)).isNull();
    assertThat(JedisServerTargets.ofNodes(emptyList())).isNull();
  }

  private static Set<String> sentinels(String... addresses) {
    return new LinkedHashSet<>(asList(addresses));
  }
}
