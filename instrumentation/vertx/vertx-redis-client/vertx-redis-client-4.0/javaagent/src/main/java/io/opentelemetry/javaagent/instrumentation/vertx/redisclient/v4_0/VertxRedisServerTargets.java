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
  public static RedisServerTarget of(@Nullable RedisOptions options) {
    if (options == null) {
      return null;
    }
    if (options.getType() == RedisClientType.SENTINEL) {
      return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(
          effectiveEndpoints(options.getEndpoints()), options.getMasterName());
    }
    if (options.getType() == RedisClientType.STANDALONE) {
      return RedisServerTarget.ofEndpoint(effectiveEndpoint(options.getEndpoint()));
    }
    return RedisServerTarget.ofUnorderedEndpoints(effectiveEndpoints(options.getEndpoints()));
  }

  private static List<String> effectiveEndpoints(List<String> connectionStrings) {
    List<String> endpoints = new ArrayList<>(connectionStrings.size());
    for (String connectionString : connectionStrings) {
      endpoints.add(effectiveEndpoint(connectionString));
    }
    return endpoints;
  }

  private static String effectiveEndpoint(String connectionString) {
    try {
      RedisURI redisUri = new RedisURI(connectionString);
      SocketAddress address = redisUri.socketAddress();
      return address.isInetSocket()
          ? RedisServerTarget.endpoint(address.host(), address.port())
          : connectionString;
    } catch (IllegalArgumentException ignored) {
      return connectionString;
    }
  }

  public static void set(RedisURI redisUri, @Nullable RedisServerTarget target) {
    if (target != null) {
      TARGET_FIELD.set(redisUri, target);
    }
  }

  @Nullable
  public static RedisServerTarget get(
      RedisStandaloneConnection connection, @Nullable RedisURI redisUri) {
    RedisServerTarget target = CONNECTION_TARGET_FIELD.get(connection);
    return target != null ? target : get(redisUri);
  }

  @Nullable
  private static RedisServerTarget get(@Nullable RedisURI redisUri) {
    return redisUri == null ? null : TARGET_FIELD.get(redisUri);
  }

  private VertxRedisServerTargets() {}
}
