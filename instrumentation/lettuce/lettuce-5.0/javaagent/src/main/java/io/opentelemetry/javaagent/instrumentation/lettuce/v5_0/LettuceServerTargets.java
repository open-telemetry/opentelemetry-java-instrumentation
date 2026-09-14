/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class LettuceServerTargets {

  private static final VirtualField<RedisClusterClient, RedisServerTarget> CLUSTER_CLIENT_TARGET =
      VirtualField.find(RedisClusterClient.class, RedisServerTarget.class);

  public static void capture(
      RedisClusterClient client, @Nullable Iterable<RedisURI> configuredUris) {
    CLUSTER_CLIENT_TARGET.set(client, ofUris(configuredUris));
  }

  public static void copy(RedisClusterClient client, RedisChannelHandler<?, ?> connection) {
    LettuceConnectionState.updateServerTarget(connection, get(client));
  }

  @Nullable
  public static RedisServerTarget get(RedisClusterClient client) {
    return CLUSTER_CLIENT_TARGET.get(client);
  }

  @Nullable
  public static RedisServerTarget of(@Nullable RedisURI redisUri) {
    if (redisUri == null) {
      return null;
    }

    RedisServerTarget sentinelTarget = ofSentinel(redisUri);
    if (sentinelTarget != null) {
      return sentinelTarget;
    }

    String socket = redisUri.getSocket();
    if (socket != null) {
      return RedisServerTarget.ofEndpoint(socket);
    }
    return RedisServerTarget.ofHostAndPort(redisUri.getHost(), redisUri.getPort());
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
      RedisServerTarget target = of((RedisURI) redisUri);
      endpoints.add(target == null ? null : render(target));
    }
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  public static RedisServerTarget ofMasterSlaveUris(List<?> redisUris) {
    if (!redisUris.isEmpty() && redisUris.get(0) instanceof RedisURI) {
      RedisURI first = (RedisURI) redisUris.get(0);
      List<RedisURI> sentinels = first.getSentinels();
      if (sentinels != null && !sentinels.isEmpty()) {
        return of(first);
      }
    }
    return ofUris(redisUris);
  }

  private static String render(RedisServerTarget target) {
    Integer port = target.getPort();
    return port == null
        ? target.getAddress()
        : RedisServerTarget.endpoint(target.getAddress(), port);
  }

  @Nullable
  private static RedisServerTarget ofSentinel(RedisURI redisUri) {
    List<RedisURI> sentinels = redisUri.getSentinels();
    if (sentinels == null || sentinels.isEmpty()) {
      return null;
    }
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
