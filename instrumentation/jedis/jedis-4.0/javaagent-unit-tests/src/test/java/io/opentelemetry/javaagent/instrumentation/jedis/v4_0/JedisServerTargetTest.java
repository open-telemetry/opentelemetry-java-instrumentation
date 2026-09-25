/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.List;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.HostAndPort;

class JedisServerTargetTest {

  private static final HostAndPort NODE_ONE = new HostAndPort("node1", 6379);
  private static final HostAndPort NODE_TWO = new HostAndPort("node2", 6380);

  @Test
  void clusterNodesAreSorted() {
    RedisServerTarget target = JedisServerTarget.ofNodes(asList(NODE_TWO, NODE_ONE));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node1:6379,node2:6380");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void shardsPreserveConfiguredOrder() {
    RedisServerTarget target = JedisServerTarget.ofShards(asList(NODE_TWO, NODE_ONE));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node2:6380,node1:6379");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void sentinelsAreSanitizedAndIncludeTheMasterName() {
    RedisServerTarget target =
        JedisServerTarget.ofSentinels(
            "mymaster",
            asList(new HostAndPort("node2", 26379), "redis://user:secret@node1:26379/0"));

    assertThat(target).isNotNull();
    assertThat(target.getAddress()).isEqualTo("node1:26379,node2:26379/mymaster");
    assertThat(target.getPort()).isNull();
  }

  @Test
  void unrepresentableMemberDropsTheWholeTarget() {
    List<Object> nodes = asList(NODE_ONE, new Object());

    assertThat(JedisServerTarget.ofNodes(nodes)).isNull();
    assertThat(JedisServerTarget.ofSentinels("mymaster", nodes)).isNull();
  }
}
