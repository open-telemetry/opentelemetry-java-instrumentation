/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.impl.RedisConnectionManagerUtil;
import io.vertx.redis.client.impl.RedisURI;
import java.util.List;
import javax.annotation.Nullable;

public final class VertxRedisServerTargets {

  @Nullable
  public static RedisServerTarget of(@Nullable RedisOptions options) {
    if (options == null) {
      return null;
    }
    if (options.getType() == RedisClientType.SENTINEL) {
      return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(
          discoveryEndpoints(options.getEndpoints()), options.getMasterName());
    }
    if (options.getType() == RedisClientType.STANDALONE) {
      return RedisServerTarget.ofEndpoint(options.getEndpoint());
    }
    if (options.getType().name().equals("REPLICATION")) {
      return RedisServerTarget.ofEndpoints(options.getEndpoints());
    }
    return RedisServerTarget.ofUnorderedEndpoints(options.getEndpoints());
  }

  public static List<String> discoveryEndpoints(List<String> connectionStrings) {
    return RedisConnectionManagerUtil.discoveryEndpoints(connectionStrings);
  }

  public static void set(RedisURI redisUri, @Nullable RedisServerTarget target) {
    RedisConnectionManagerUtil.setRedisUriTarget(redisUri, target);
  }

  @Nullable
  public static RedisConnectionManagerUtil.CapturedTarget get(@Nullable RedisURI redisUri) {
    return RedisConnectionManagerUtil.getRedisUriTarget(redisUri);
  }

  private VertxRedisServerTargets() {}
}
