/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
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
      @Nullable String masterName, @Nullable Collection<?> sentinels) {
    return RedisServerTarget.ofEndpointsAndLogicalName(endpointStrings(sentinels), masterName);
  }

  /**
   * The target of a client configured with several nodes, built from the {@code host:port}
   * rendering each node carries.
   */
  @Nullable
  public static RedisServerTarget ofNodes(@Nullable Collection<?> nodes) {
    return RedisServerTarget.ofEndpoints(endpointStrings(nodes));
  }

  @Nullable
  private static List<String> endpointStrings(@Nullable Collection<?> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(nodes.size());
    for (Object node : nodes) {
      if (node != null) {
        endpoints.add(node.toString());
      }
    }
    return endpoints;
  }

  private JedisServerTargets() {}
}
