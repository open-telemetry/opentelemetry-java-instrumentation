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

// visible for testing
public class JedisServerTarget {

  @Nullable
  public static RedisServerTarget ofNodes(@Nullable Collection<?> nodes) {
    return RedisServerTarget.ofUnorderedEndpoints(endpointStrings(nodes));
  }

  @Nullable
  public static RedisServerTarget ofShards(@Nullable List<HostAndPort> shards) {
    return RedisServerTarget.ofEndpoints(endpointStrings(shards));
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
        } else if (sentinel instanceof String) {
          endpoints.add(RedisServerTarget.normalizeHostAndPort((String) sentinel));
        } else {
          endpoints.add(null);
        }
      }
    }
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(endpoints, masterName);
  }

  @Nullable
  private static List<String> endpointStrings(@Nullable Collection<?> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(nodes.size());
    for (Object value : nodes) {
      if (value instanceof HostAndPort) {
        HostAndPort node = (HostAndPort) value;
        endpoints.add(RedisServerTarget.endpoint(node.getHost(), node.getPort()));
      } else {
        endpoints.add(null);
      }
    }
    return endpoints;
  }

  private JedisServerTarget() {}
}
