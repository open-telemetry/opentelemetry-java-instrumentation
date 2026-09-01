/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisShardInfo;

public class JedisServerTargets {

  @Nullable
  public static RedisServerTarget ofShards(@Nullable List<JedisShardInfo> shards) {
    if (shards == null || shards.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(shards.size());
    for (JedisShardInfo shard : shards) {
      endpoints.add(
          shard == null ? null : RedisServerTarget.endpoint(shard.getHost(), shard.getPort()));
    }
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  public static RedisServerTarget ofSentinels(
      @Nullable String masterName, @Nullable Collection<?> sentinels) {
    List<String> endpoints = null;
    if (sentinels != null) {
      endpoints = new ArrayList<>(sentinels.size());
      for (Object sentinel : sentinels) {
        if (sentinel instanceof HostAndPort) {
          HostAndPort hostAndPort = (HostAndPort) sentinel;
          endpoints.add(RedisServerTarget.endpoint(hostAndPort.getHost(), hostAndPort.getPort()));
        } else {
          endpoints.add(
              sentinel == null
                  ? null
                  : RedisServerTarget.normalizeHostAndPort(sentinel.toString()));
        }
      }
    }
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(endpoints, masterName);
  }

  @Nullable
  public static RedisServerTarget ofNodes(@Nullable Collection<HostAndPort> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(nodes.size());
    for (HostAndPort node : nodes) {
      endpoints.add(
          node == null ? null : RedisServerTarget.endpoint(node.getHost(), node.getPort()));
    }
    return RedisServerTarget.ofUnorderedEndpoints(endpoints);
  }

  private JedisServerTargets() {}
}
