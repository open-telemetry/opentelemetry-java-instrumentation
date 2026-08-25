/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisShardInfo;

/** Renders the target a client was configured with from the endpoints it was built with. */
public final class JedisServerTargets {

  /** The target of a sharded client, built from its shard list. */
  @Nullable
  public static RedisServerTarget ofShards(@Nullable List<JedisShardInfo> shards) {
    if (shards == null || shards.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(shards.size());
    for (JedisShardInfo shard : shards) {
      endpoints.add(RedisServerTarget.endpoint(shard.getHost(), shard.getPort()));
    }
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  /** The target of a Sentinel backed client, scoped by its sentinels and master name. */
  @Nullable
  public static RedisServerTarget ofSentinels(
      @Nullable String masterName, @Nullable Collection<String> sentinels) {
    List<String> endpoints = sentinels == null ? null : new ArrayList<>(sentinels);
    return RedisServerTarget.ofEndpointsAndLogicalName(endpoints, masterName);
  }

  /** The target of a client configured with several nodes, built from every node it was given. */
  @Nullable
  public static RedisServerTarget ofNodes(@Nullable Collection<HostAndPort> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(nodes.size());
    for (HostAndPort node : nodes) {
      if (node != null) {
        endpoints.add(RedisServerTarget.endpoint(node.getHost(), node.getPort()));
      }
    }
    Collections.sort(endpoints);
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  private JedisServerTargets() {}
}
