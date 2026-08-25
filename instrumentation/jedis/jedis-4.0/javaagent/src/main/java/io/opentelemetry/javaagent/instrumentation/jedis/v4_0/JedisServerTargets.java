/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;

/** Renders the target a client was configured with from the endpoints it was built with. */
public final class JedisServerTargets {

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
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  /** The target of a Sentinel backed client, scoped by its sentinels and master name. */
  @Nullable
  public static RedisServerTarget ofSentinels(
      @Nullable String masterName, @Nullable Collection<?> sentinels) {
    List<String> endpoints = null;
    if (sentinels != null) {
      endpoints = new ArrayList<>(sentinels.size());
      for (Object sentinel : sentinels) {
        if (sentinel != null) {
          endpoints.add(sentinel.toString());
        }
      }
    }
    return RedisServerTarget.ofEndpointsAndLogicalName(endpoints, masterName);
  }

  /**
   * Finds the Sentinel collection in constructor arguments whose position varies between Jedis
   * releases.
   */
  @Nullable
  public static RedisServerTarget ofSentinelsFromArguments(
      @Nullable String masterName, @Nullable Object[] arguments) {
    if (arguments != null) {
      for (Object argument : arguments) {
        if (argument instanceof Collection) {
          return ofSentinels(masterName, (Collection<?>) argument);
        }
      }
    }
    return RedisServerTarget.ofEndpointsAndLogicalName(null, masterName);
  }

  private JedisServerTargets() {}
}
