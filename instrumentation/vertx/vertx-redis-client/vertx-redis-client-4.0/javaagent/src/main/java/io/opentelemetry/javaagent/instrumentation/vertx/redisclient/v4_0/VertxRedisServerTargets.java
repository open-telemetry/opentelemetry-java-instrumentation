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

public final class VertxRedisServerTargets {

  private static final VirtualField<RedisURI, CapturedTarget> TARGET_FIELD =
      VirtualField.find(RedisURI.class, CapturedTarget.class);

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
    List<String> endpoints = new ArrayList<>(connectionStrings.size());
    for (String connectionString : connectionStrings) {
      try {
        RedisURI redisUri = new RedisURI(connectionString);
        SocketAddress address = redisUri.socketAddress();
        endpoints.add(
            address.isInetSocket()
                ? RedisServerTarget.endpoint(address.host(), address.port())
                : connectionString);
      } catch (IllegalArgumentException ignored) {
        endpoints.add(connectionString);
      }
    }
    return endpoints;
  }

  public static void set(RedisURI redisUri, @Nullable RedisServerTarget target) {
    TARGET_FIELD.set(redisUri, new CapturedTarget(target));
  }

  @Nullable
  public static CapturedTarget get(@Nullable RedisURI redisUri) {
    return redisUri == null ? null : TARGET_FIELD.get(redisUri);
  }

  static final class CapturedTarget {
    @Nullable private final RedisServerTarget target;

    private CapturedTarget(@Nullable RedisServerTarget target) {
      this.target = target;
    }

    @Nullable
    RedisServerTarget getTarget() {
      return target;
    }
  }

  private VertxRedisServerTargets() {}
}
