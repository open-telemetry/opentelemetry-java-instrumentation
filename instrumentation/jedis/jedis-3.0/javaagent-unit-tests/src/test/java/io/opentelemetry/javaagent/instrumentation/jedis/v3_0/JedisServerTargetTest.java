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
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisShardInfo;

class JedisServerTargetTest {

  @Test
  void shardsKeepTheirOrder() {
    RedisServerTarget target =
        JedisServerTarget.ofShards(
            asList(new JedisShardInfo("shard2", 6380), new JedisShardInfo("shard1", 6379)));

    assertThat(target.getAddress()).isEqualTo("shard2:6380,shard1:6379");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void shardsWithSharedNonDefaultPortKeepPortsInline() {
    RedisServerTarget target =
        JedisServerTarget.ofShards(
            asList(new JedisShardInfo("shard1", 6380), new JedisShardInfo("shard2", 6380)));

    assertThat(target.getAddress()).isEqualTo("shard1:6380,shard2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void oneShardOmitsItsDefaultPort() {
    RedisServerTarget target =
        JedisServerTarget.ofShards(singletonList(new JedisShardInfo("shard1", 6379)));

    assertThat(target.getAddress()).isEqualTo("shard1");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void noShards() {
    assertThat(JedisServerTarget.ofShards(null)).isNull();
    assertThat(JedisServerTarget.ofShards(emptyList())).isNull();
  }

  @Test
  void clusterNodesAreSorted() {
    RedisServerTarget target =
        JedisServerTarget.ofNodes(
            new LinkedHashSet<>(
                asList(new HostAndPort("node2", 7001), new HostAndPort("node1", 7000))));

    assertThat(target.getAddress()).isEqualTo("node1:7000,node2:7001");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void clusterNodesIncludeAtMostFirstFiveAfterSorting() {
    RedisServerTarget target =
        JedisServerTarget.ofNodes(
            asList(
                new HostAndPort("node6", 6379),
                new HostAndPort("node5", 6379),
                new HostAndPort("node4", 6379),
                new HostAndPort("node3", 6379),
                new HostAndPort("node2", 6379),
                new HostAndPort("node1", 6379)));

    assertThat(target.getAddress()).isEqualTo("node1,node2,node3,node4,node5");
  }

  @Test
  void oneClusterNodeKeepsItsPort() {
    RedisServerTarget target =
        JedisServerTarget.ofNodes(singletonList(new HostAndPort("node1", 7000)));

    assertThat(target.getAddress()).isEqualTo("node1");
    assertThat(target.getPort()).isEqualTo(7000);
  }

  @Test
  void noClusterNodes() {
    assertThat(JedisServerTarget.ofNodes(null)).isNull();
    assertThat(JedisServerTarget.ofNodes(emptyList())).isNull();
  }

  @Test
  void clusterNodeListWithNullMemberFailsClosed() {
    assertThat(JedisServerTarget.ofNodes(asList(new HostAndPort("node1", 7000), null))).isNull();
  }
}
