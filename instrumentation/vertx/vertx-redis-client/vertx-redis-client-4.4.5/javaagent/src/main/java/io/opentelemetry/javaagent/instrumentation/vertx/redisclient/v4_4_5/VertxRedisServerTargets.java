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
import io.vertx.redis.client.impl.RedisStandaloneConnection;
import io.vertx.redis.client.impl.RedisURI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public final class VertxRedisServerTargets {

  private static final VirtualField<RedisURI, RedisServerTarget> TARGET_FIELD =
      VirtualField.find(RedisURI.class, RedisServerTarget.class);
  private static final VirtualField<RedisStandaloneConnection, RedisServerTarget>
      CONNECTION_TARGET_FIELD =
          VirtualField.find(RedisStandaloneConnection.class, RedisServerTarget.class);
  private static final ThreadLocal<RedisServerTarget> currentTarget = new ThreadLocal<>();

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

  public static void setCurrent(@Nullable RedisServerTarget target) {
    currentTarget.set(target);
  }

  public static void clearCurrent() {
    currentTarget.remove();
  }

  public static void setConnectionTarget(RedisStandaloneConnection connection, RedisURI redisUri) {
    RedisServerTarget target = currentTarget.get();
    if (target == null) {
      target = TARGET_FIELD.get(redisUri);
    }
    if (target != null) {
      CONNECTION_TARGET_FIELD.set(connection, target);
    }
  }

  @Nullable
  public static RedisServerTarget getConnectionTarget(RedisStandaloneConnection connection) {
    return CONNECTION_TARGET_FIELD.get(connection);
  }

  private VertxRedisServerTargets() {}
}
