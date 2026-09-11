/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import redis.clients.jedis.JedisShardInfo;

class JedisServerTargetsTest {

  @ParameterizedTest
  @NullAndEmptySource
  void noShards(List<JedisShardInfo> shards) {
    assertThat(JedisServerTargets.ofShards(shards)).isNull();
  }

  @ParameterizedTest
  @MethodSource("shardCases")
  void createsTargetFromShards(List<JedisShardInfo> shards, String expectedAddress) {
    RedisServerTarget target = JedisServerTargets.ofShards(shards);

    assertThat(target.getAddress()).isEqualTo(expectedAddress);
    assertThat(target.getPort()).isNull();
  }

  private static Stream<Arguments> shardCases() {
    return Stream.of(
        argumentSet(
            "keeps shard order",
            asList(new JedisShardInfo("shard2", 6380), new JedisShardInfo("shard1", 6379)),
            "shard2:6380,shard1:6379"),
        argumentSet(
            "omits the default port",
            singletonList(new JedisShardInfo("shard1", 6379)),
            "shard1"),
        argumentSet(
            "preserves duplicate shards",
            asList(new JedisShardInfo("shard1", 6379), new JedisShardInfo("shard1", 6379)),
            "shard1,shard1"));
  }

  @Test
  void shardListWithNullMemberFailsClosed() {
    assertThat(JedisServerTargets.ofShards(asList(new JedisShardInfo("shard1", 6379), null)))
        .isNull();
  }
}
