/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class LettuceServerTargets {

  @Nullable
  public static RedisServerTarget of(@Nullable RedisURI redisUri) {
    if (redisUri == null) {
      return null;
    }

    if (isSentinel(redisUri)) {
      return ofSentinel(redisUri);
    }

    String socket = redisUri.getSocket();
    return socket != null
        ? RedisServerTarget.ofEndpoint(socket)
        : RedisServerTarget.ofHostAndPort(redisUri.getHost(), redisUri.getPort());
  }

  @Nullable
  public static RedisServerTarget ofUris(@Nullable Iterable<?> redisUris) {
    if (redisUris == null) {
      return null;
    }
    List<String> endpoints = new ArrayList<>();
    for (Object redisUri : redisUris) {
      if (!(redisUri instanceof RedisURI)) {
        endpoints.add(null);
        continue;
      }
      endpoints.add(endpoint((RedisURI) redisUri));
    }
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  public static RedisServerTarget ofMasterSlaveUris(List<?> redisUris) {
    if (!redisUris.isEmpty() && redisUris.get(0) instanceof RedisURI) {
      RedisURI first = (RedisURI) redisUris.get(0);
      // MasterSlave.connect switches the whole list to sentinel mode based on the first URI alone.
      if (isSentinel(first)) {
        return of(first);
      }
    }
    return ofUris(redisUris);
  }

  @Nullable
  private static String endpoint(RedisURI redisUri) {
    if (!isSentinel(redisUri)) {
      String socket = redisUri.getSocket();
      return socket != null
          ? socket
          : RedisServerTarget.endpoint(redisUri.getHost(), redisUri.getPort());
    }

    RedisServerTarget target = ofSentinel(redisUri);
    if (target == null) {
      return null;
    }
    Integer port = target.getPort();
    return port == null
        ? target.getAddress()
        : RedisServerTarget.endpoint(target.getAddress(), port);
  }

  // A master name alone does not select sentinel mode: lettuce resolves the master through the
  // sentinels and otherwise connects to the host and port of the URI itself.
  private static boolean isSentinel(RedisURI redisUri) {
    List<RedisURI> sentinels = redisUri.getSentinels();
    return sentinels != null && !sentinels.isEmpty();
  }

  @Nullable
  private static RedisServerTarget ofSentinel(RedisURI redisUri) {
    List<RedisURI> sentinels = redisUri.getSentinels();
    List<String> endpoints = new ArrayList<>(sentinels.size());
    for (RedisURI sentinel : sentinels) {
      String socket = sentinel.getSocket();
      endpoints.add(
          socket != null
              ? socket
              : RedisServerTarget.endpoint(sentinel.getHost(), sentinel.getPort()));
    }
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(
        endpoints, redisUri.getSentinelMasterId());
  }

  private LettuceServerTargets() {}
}
