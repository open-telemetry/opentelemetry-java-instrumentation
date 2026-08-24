/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.impl.RedisURI;
import javax.annotation.Nullable;

/**
 * Renders the target a client was configured with from the {@link RedisOptions} it was created
 * with, and keeps it on the {@link RedisURI} of the endpoint the client connects through.
 *
 * <p>{@code RedisOptions} is mutable, so the target is rendered once while the client is being set
 * up and kept as an immutable value from then on.
 */
public final class VertxRedisServerTargets {

  private static final VirtualField<RedisURI, RedisServerTarget> targetField =
      VirtualField.find(RedisURI.class, RedisServerTarget.class);

  @Nullable
  public static RedisServerTarget of(@Nullable RedisOptions options) {
    if (options == null) {
      return null;
    }
    if (options.getType() == RedisClientType.SENTINEL) {
      // the master name is only meaningful for a sentinel client; every other client type carries
      // the default name whether or not it was configured
      RedisServerTarget masterTarget = RedisServerTarget.ofLogicalName(options.getMasterName());
      if (masterTarget != null) {
        return masterTarget;
      }
      return RedisServerTarget.ofEndpoints(options.getEndpoints());
    }
    if (options.getType() == RedisClientType.STANDALONE) {
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

  @Nullable
  public static RedisServerTarget get(@Nullable RedisURI redisUri) {
    return redisUri == null ? null : targetField.get(redisUri);
  }

  private VertxRedisServerTargets() {}
}
