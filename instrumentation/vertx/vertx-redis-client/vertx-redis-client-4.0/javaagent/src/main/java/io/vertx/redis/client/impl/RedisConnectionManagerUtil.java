/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.vertx.redis.client.impl;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.internal.ScopedThreadValue;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

public class RedisConnectionManagerUtil {

  private static final VirtualField<RedisConnectionManager, RedisServerTarget> TARGET_FIELD =
      VirtualField.find(RedisConnectionManager.class, RedisServerTarget.class);
  private static final VirtualField<RedisURI, CapturedTarget> REDIS_URI_TARGET_FIELD =
      VirtualField.find(RedisURI.class, CapturedTarget.class);

  private static final ScopedThreadValue<RedisServerTarget> currentServerTarget =
      new ScopedThreadValue<>();

  public static void setServerTarget(Object manager, @Nullable RedisServerTarget target) {
    TARGET_FIELD.set((RedisConnectionManager) manager, target);
  }

  @Nullable
  public static RedisServerTarget getServerTarget(Object manager) {
    return TARGET_FIELD.get((RedisConnectionManager) manager);
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

  public static void setRedisUriTarget(
      RedisURI redisUri, @Nullable RedisServerTarget serverTarget) {
    REDIS_URI_TARGET_FIELD.set(redisUri, new CapturedTarget(serverTarget));
  }

  @Nullable
  public static CapturedTarget getRedisUriTarget(@Nullable RedisURI redisUri) {
    return redisUri == null ? null : REDIS_URI_TARGET_FIELD.get(redisUri);
  }

  public static ScopedThreadValue<RedisServerTarget> currentServerTarget() {
    return currentServerTarget;
  }

  private RedisConnectionManagerUtil() {}

  public static final class CapturedTarget {
    @Nullable private final RedisServerTarget target;

    private CapturedTarget(@Nullable RedisServerTarget target) {
      this.target = target;
    }

    @Nullable
    public RedisServerTarget getTarget() {
      return target;
    }
  }
}
