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
      return RedisServerTarget.ofEndpoint(effectiveEndpoint(options.getEndpoint()));
    }
    return RedisServerTarget.ofEndpoints(options.getEndpoints());
  }

  private static List<String> sentinelEndpoints(List<String> connectionStrings) {
    List<String> endpoints = new ArrayList<>(connectionStrings.size());
    for (String connectionString : connectionStrings) {
      endpoints.add(effectiveEndpoint(connectionString));
    }
    return endpoints;
  }

  private static String effectiveEndpoint(String connectionString) {
    RedisURI redisUri = new RedisURI(connectionString);
    SocketAddress address = redisUri.socketAddress();
    return address.isInetSocket()
        ? RedisServerTarget.endpoint(address.host(), address.port())
        : connectionString;
  }

  public static void setEndpoint(RedisURI redisUri, String connectionString) {
    set(redisUri, RedisServerTarget.ofEndpoint(effectiveEndpoint(connectionString)));
  }

  public static void set(RedisURI redisUri, @Nullable RedisServerTarget target) {
    if (target != null) {
      TARGET_FIELD.set(redisUri, target);
    }
  }

  public static void setConnectionTarget(RedisStandaloneConnection connection, RedisURI redisUri) {
    RedisServerTarget target = TARGET_FIELD.get(redisUri);
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
