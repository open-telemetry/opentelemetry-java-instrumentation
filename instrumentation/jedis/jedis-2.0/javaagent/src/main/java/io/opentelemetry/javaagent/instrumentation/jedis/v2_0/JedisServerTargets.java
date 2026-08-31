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

public final class JedisServerTargets {

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

  @Nullable
  public static RedisServerTarget ofSentinels(
      @Nullable String masterName, @Nullable Collection<?> sentinels) {
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(
        endpointStrings(sentinels), masterName);
  }

  @Nullable
  public static RedisServerTarget ofNodes(@Nullable Collection<?> nodes) {
    List<String> endpoints = endpointStrings(nodes);
    return RedisServerTarget.ofUnorderedEndpoints(endpoints);
  }

  @Nullable
  private static List<String> endpointStrings(@Nullable Collection<?> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(nodes.size());
    for (Object node : nodes) {
      if (node != null) {
        endpoints.add(normalizeEndpoint(node.toString()));
      }
    }
    return endpoints;
  }

  private static String normalizeEndpoint(String endpoint) {
    if (endpoint.startsWith("[") || endpoint.indexOf("://") >= 0) {
      return endpoint;
    }
    int firstColon = endpoint.indexOf(':');
    int lastColon = endpoint.lastIndexOf(':');
    if (firstColon < 0 || firstColon == lastColon) {
      return endpoint;
    }
    if (endpoint.charAt(lastColon - 1) == ':') {
      return endpoint;
    }
    String port = endpoint.substring(lastColon + 1);
    if (!isPort(port)) {
      return endpoint;
    }
    return "[" + endpoint.substring(0, lastColon) + "]:" + port;
  }

  private static boolean isPort(String value) {
    if (value.isEmpty() || value.length() > 5) {
      return false;
    }
    int port = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
      port = port * 10 + (c - '0');
    }
    return port <= 65535;
  }

  private JedisServerTargets() {}
}
