/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisShardInfo;

class JedisServerTargetsTest {

  @Test
  void noShards() {
    assertThat(JedisServerTargets.ofShards(null)).isNull();
    assertThat(JedisServerTargets.ofShards(emptyList())).isNull();
  }

  @Test
  void shardsKeepTheirOrder() {
    RedisServerTarget target =
        JedisServerTargets.ofShards(
            asList(new JedisShardInfo("shard2", 6380), new JedisShardInfo("shard1", 6379)));

    assertThat(target.getAddress()).isEqualTo("shard2:6380,shard1:6379");
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
  void shardListWithNullMemberFailsClosed() {
    assertThat(JedisServerTargets.ofShards(asList(new JedisShardInfo("shard1", 6379), null)))
        .isNull();
  }
}
