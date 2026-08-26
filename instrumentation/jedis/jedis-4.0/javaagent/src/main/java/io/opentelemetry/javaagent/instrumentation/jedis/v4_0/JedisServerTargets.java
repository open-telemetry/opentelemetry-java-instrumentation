/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;

public final class JedisServerTargets {

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

  // Sentinel constructor argument order varies across supported Jedis 4 releases.
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
