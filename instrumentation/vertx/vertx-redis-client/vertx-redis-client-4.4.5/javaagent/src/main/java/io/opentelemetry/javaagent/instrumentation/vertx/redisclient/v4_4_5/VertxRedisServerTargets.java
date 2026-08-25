/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.net.SocketAddress;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.RedisSentinelConnectOptions;
import io.vertx.redis.client.RedisStandaloneConnectOptions;
import io.vertx.redis.client.impl.RedisURI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Renders the target a client was configured with from the {@link RedisConnectOptions} it connects
 * with, and keeps it on the {@link RedisURI} of the endpoint the client connects through.
 *
 * <p>{@code RedisConnectOptions} is mutable, so the target is rendered once while the client is
 * being set up and kept as an immutable value from then on.
 */
public final class VertxRedisServerTargets {

  private static final VirtualField<RedisURI, RedisServerTarget> TARGET_FIELD =
      VirtualField.find(RedisURI.class, RedisServerTarget.class);

  @Nullable
  public static RedisServerTarget of(@Nullable RedisConnectOptions options) {
    if (options == null) {
      return null;
    }
    if (options instanceof RedisSentinelConnectOptions) {
      return RedisServerTarget.ofEndpointsAndLogicalName(
          sentinelEndpoints(options.getEndpoints()),
          ((RedisSentinelConnectOptions) options).getMasterName());
    }
    if (options instanceof RedisStandaloneConnectOptions) {
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

  private VertxRedisServerTargets() {}
}
