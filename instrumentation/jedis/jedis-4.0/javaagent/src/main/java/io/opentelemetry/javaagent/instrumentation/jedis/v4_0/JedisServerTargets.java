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

public final class JedisServerTargets {

  @Nullable
  public static RedisServerTarget ofNodes(@Nullable Collection<HostAndPort> nodes) {
    return RedisServerTarget.ofUnorderedEndpoints(endpointStrings(nodes));
  }

  @Nullable
  public static RedisServerTarget ofShards(@Nullable List<HostAndPort> shards) {
    return RedisServerTarget.ofEndpoints(endpointStrings(shards));
  }

  @Nullable
  private static List<String> endpointStrings(@Nullable Collection<HostAndPort> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(nodes.size());
    for (HostAndPort node : nodes) {
      if (node != null) {
        endpoints.add(RedisServerTarget.endpoint(node.getHost(), node.getPort()));
      }
    }
    return endpoints;
  }

  @Nullable
  public static RedisServerTarget ofSentinels(
      @Nullable String masterName, @Nullable Collection<?> sentinels) {
    List<String> endpoints = null;
    if (sentinels != null) {
      endpoints = new ArrayList<>(sentinels.size());
      for (Object sentinel : sentinels) {
        if (sentinel != null) {
          if (sentinel instanceof HostAndPort) {
            HostAndPort hostAndPort = (HostAndPort) sentinel;
            endpoints.add(RedisServerTarget.endpoint(hostAndPort.getHost(), hostAndPort.getPort()));
          } else {
            endpoints.add(normalizeSentinelEndpoint(sentinel.toString()));
          }
        }
      }
    }
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(endpoints, masterName);
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
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(null, masterName);
  }

  private static String normalizeSentinelEndpoint(String endpoint) {
    if (endpoint.startsWith("[")) {
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
