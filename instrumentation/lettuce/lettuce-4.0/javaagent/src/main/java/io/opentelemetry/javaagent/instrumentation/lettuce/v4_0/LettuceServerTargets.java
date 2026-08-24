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

/**
 * Renders the target a connection was configured with from the {@link RedisURI} the client was
 * asked to connect to.
 *
 * <p>A {@code RedisURI} is mutable, so the target is rendered once while the connection is being
 * established and kept as an immutable value from then on.
 */
public final class LettuceServerTargets {

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
  private static RedisServerTarget ofSentinel(RedisURI redisUri) {
    RedisServerTarget masterTarget =
        RedisServerTarget.ofLogicalName(redisUri.getSentinelMasterId());
    if (masterTarget != null) {
      return masterTarget;
    }
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
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  private LettuceServerTargets() {}
}
