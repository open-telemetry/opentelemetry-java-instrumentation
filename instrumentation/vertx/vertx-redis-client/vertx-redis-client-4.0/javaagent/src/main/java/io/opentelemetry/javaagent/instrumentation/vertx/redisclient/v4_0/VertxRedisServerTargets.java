/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.net.SocketAddress;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.impl.RedisURI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Renders the target a client was configured with from the {@link RedisOptions} it was created
 * with, and keeps it on the {@link RedisURI} of the endpoint the client connects through.
 *
 * <p>{@code RedisOptions} is mutable, so the target is rendered once while the client is being set
 * up and kept as an immutable value from then on.
 */
public final class VertxRedisServerTargets {

  private static final VirtualField<RedisURI, RedisServerTarget> TARGET_FIELD =
      VirtualField.find(RedisURI.class, RedisServerTarget.class);

  @Nullable
  public static RedisServerTarget of(@Nullable RedisOptions options) {
    if (options == null) {
      return null;
    }
    if (options.getType() == RedisClientType.SENTINEL) {
      // the master name is only meaningful for a sentinel client; every other client type carries
      // the default name whether or not it was configured
      return RedisServerTarget.ofEndpointsAndLogicalName(
          sentinelEndpoints(options.getEndpoints()), options.getMasterName());
    }
    if (options.getType() == RedisClientType.STANDALONE) {
      // a standalone client only ever talks to the endpoint it picks, even when more are configured
      return RedisServerTarget.ofEndpoint(options.getEndpoint());
    }
    return RedisServerTarget.ofEndpoints(options.getEndpoints());
  }

  private static List<String> sentinelEndpoints(List<String> connectionStrings) {
    List<String> endpoints = new ArrayList<>(connectionStrings.size());
    for (String connectionString : connectionStrings) {
      RedisURI redisUri = new RedisURI(connectionString);
      SocketAddress address = redisUri.socketAddress();
      endpoints.add(
          address.isInetSocket()
              ? RedisServerTarget.endpoint(address.host(), address.port())
              : connectionString);
    }
    return endpoints;
  }

  public static void set(RedisURI redisUri, @Nullable RedisServerTarget target) {
    if (target != null) {
      TARGET_FIELD.set(redisUri, target);
    }
  }

  @Nullable
  public static RedisServerTarget get(@Nullable RedisURI redisUri) {
    return redisUri == null ? null : TARGET_FIELD.get(redisUri);
  }

  private VertxRedisServerTargets() {}
}
