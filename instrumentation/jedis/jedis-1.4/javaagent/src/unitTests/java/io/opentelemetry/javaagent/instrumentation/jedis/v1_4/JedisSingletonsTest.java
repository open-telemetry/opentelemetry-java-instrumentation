/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;
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
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisShardInfo;
import redis.clients.jedis.ShardedJedis;

class JedisSingletonsTest {

  @ParameterizedTest
  @NullAndEmptySource
  void noShards(List<JedisShardInfo> shards) {
    assertThat(JedisSingletons.createServerTarget(shards)).isNull();
  }

  @ParameterizedTest
  @MethodSource("shardCases")
  void createsTargetFromShards(List<JedisShardInfo> shards, String expectedAddress) {
    RedisServerTarget target = JedisSingletons.createServerTarget(shards);

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
            "omits the default port", singletonList(new JedisShardInfo("shard1", 6379)), "shard1"),
        argumentSet(
            "preserves duplicate shards",
            asList(new JedisShardInfo("shard1", 6379), new JedisShardInfo("shard1", 6379)),
            "shard1,shard1"));
  }

  @Test
  void shardListWithNullMemberFailsClosed() {
    assertThat(JedisSingletons.createServerTarget(asList(new JedisShardInfo("shard1", 6379), null)))
        .isNull();
  }

  @Test
  void shardListKeepsAtMostFiveEndpoints() {
    List<JedisShardInfo> shards =
        asList(
            new JedisShardInfo("shard1"),
            new JedisShardInfo("shard2"),
            new JedisShardInfo("shard3"),
            new JedisShardInfo("shard4"),
            new JedisShardInfo("shard5"),
            new JedisShardInfo("shard6"));

    assertThat(JedisSingletons.createServerTarget(shards).getAddress())
        .isEqualTo("shard1,shard2,shard3,shard4,shard5");
  }

  @Test
  void shardListKeepsCompleteEndpointsWithin255Characters() {
    String first = String.join(".", nCopies(2, String.join("", nCopies(60, "a"))));
    String second = String.join(".", nCopies(2, String.join("", nCopies(60, "b"))));
    String third = String.join(".", nCopies(2, String.join("", nCopies(60, "c"))));

    RedisServerTarget target =
        JedisSingletons.createServerTarget(
            asList(
                new JedisShardInfo(first), new JedisShardInfo(second), new JedisShardInfo(third)));

    assertThat(target.getAddress()).isEqualTo(first + "," + second);
    assertThat(target.getAddress()).hasSizeLessThanOrEqualTo(255);
  }

  @Test
  void captureConnectionTargetFallsBackToHostAndPortWhenNoConfiguredTargetExists() {
    Connection connection = new Connection("direct", 6380);

    JedisSingletons.captureConnectionTarget(connection);

    RedisServerTarget target = JedisSingletons.connectionTarget(connection);
    assertThat(target.getAddress()).isEqualTo("direct");
    assertThat(target.getPort()).isEqualTo(6380);
  }

  @Test
  void captureShardedConnectionTargetsOverridesHostAndPort() {
    JedisShardInfo first = new JedisShardInfo("first", 6380);
    JedisShardInfo second = new JedisShardInfo("second", 6381);
    List<JedisShardInfo> shards = asList(first, second);
    ShardedJedis sharded = new ShardedJedis(shards);

    JedisSingletons.captureShardedConnectionTargets(sharded, shards);

    RedisServerTarget target = JedisSingletons.connectionTarget(first.getResource().getClient());
    assertThat(target.getAddress()).isEqualTo("first:6380,second:6381");
    assertThat(target.getPort()).isNull();
  }
}
