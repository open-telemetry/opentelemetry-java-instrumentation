/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v1_4;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.context.Scope;
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
    assertThat(
            JedisSingletons.createServerTarget(
                asList(new JedisShardInfo("shard1", 6379), null)))
        .isNull();
  }

  @Test
  void captureConnectionTargetFallsBackToHostAndPortWhenNoScopeIsActive() {
    Connection connection = new Connection("direct", 6380);

    JedisSingletons.captureConnectionTarget(connection);

    RedisServerTarget target = JedisSingletons.connectionTarget(connection);
    assertThat(target.getAddress()).isEqualTo("direct");
    assertThat(target.getPort()).isEqualTo(6380);
  }

  @Test
  void captureConnectionTargetUsesActiveScopeInsteadOfHostAndPort() {
    Connection connection = new Connection("direct", 6379);
    RedisServerTarget configuredTarget = RedisServerTarget.ofHostAndPort("configured", 6380);

    try (Scope ignored = JedisSingletons.openConfiguredTargetScope(configuredTarget)) {
      JedisSingletons.captureConnectionTarget(connection);
    }

    assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(configuredTarget);
  }

  @Test
  void connectionTargetPrefersActiveScopeOverAttachedTargetThenFallsBackOnClose() {
    Connection connection = new Connection("direct", 6379);
    RedisServerTarget attachedTarget = RedisServerTarget.ofHostAndPort("attached", 6380);
    RedisServerTarget scopedTarget = RedisServerTarget.ofHostAndPort("scoped", 6381);

    try (Scope ignored = JedisSingletons.openConfiguredTargetScope(attachedTarget)) {
      JedisSingletons.captureConnectionTarget(connection);
    }
    assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(attachedTarget);

    try (Scope ignored = JedisSingletons.openConfiguredTargetScope(scopedTarget)) {
      assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(scopedTarget);
    }

    assertThat(JedisSingletons.connectionTarget(connection)).isSameAs(attachedTarget);
  }
}
