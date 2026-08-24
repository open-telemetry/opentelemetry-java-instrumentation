/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.RedisSentinelConnectOptions;
import io.vertx.redis.client.RedisStandaloneConnectOptions;
import io.vertx.redis.client.impl.RedisURI;
import javax.annotation.Nullable;

/**
 * Renders the target a client was configured with from the {@link RedisConnectOptions} it connects
 * with, and keeps it on the {@link RedisURI} of the endpoint the client connects through.
 *
 * <p>{@code RedisConnectOptions} is mutable, so the target is rendered once while the client is
 * being set up and kept as an immutable value from then on.
 */
public final class VertxRedisServerTargets {

  private static final VirtualField<RedisURI, RedisServerTarget> targetField =
      VirtualField.find(RedisURI.class, RedisServerTarget.class);

  @Nullable
  public static RedisServerTarget of(@Nullable RedisConnectOptions options) {
    if (options == null) {
      return null;
    }
    if (options instanceof RedisSentinelConnectOptions) {
      RedisServerTarget masterTarget =
          RedisServerTarget.ofLogicalName(((RedisSentinelConnectOptions) options).getMasterName());
      if (masterTarget != null) {
        return masterTarget;
      }
      return RedisServerTarget.ofEndpoints(options.getEndpoints());
    }
    if (options instanceof RedisStandaloneConnectOptions) {
      // a standalone client only ever talks to the endpoint it picks, even when more are configured
      return RedisServerTarget.ofEndpoint(options.getEndpoint());
    }
    return RedisServerTarget.ofEndpoints(options.getEndpoints());
  }

  public static void set(RedisURI redisUri, @Nullable RedisServerTarget target) {
    if (target != null) {
      targetField.set(redisUri, target);
    }
  }

  private VertxRedisServerTargets() {}
}
