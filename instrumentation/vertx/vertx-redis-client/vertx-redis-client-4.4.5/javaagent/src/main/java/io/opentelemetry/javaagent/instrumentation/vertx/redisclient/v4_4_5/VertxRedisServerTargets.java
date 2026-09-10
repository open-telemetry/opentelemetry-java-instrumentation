/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import static io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0.VertxRedisServerTargets.discoveryEndpoints;
import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.RedisSentinelConnectOptions;
import io.vertx.redis.client.RedisStandaloneConnectOptions;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class VertxRedisServerTargets {

  private static final Logger logger = Logger.getLogger(VertxRedisServerTargets.class.getName());

  private static final ThreadLocal<TargetFrame> factoryTarget = new ThreadLocal<>();

  @Nullable
  public static RedisServerTarget of(@Nullable RedisOptions options) {
    if (options == null) {
      return null;
    }
    // replication topology is resolved here, rather than left to the 4.0 helper's fallback, so
    // that a STATIC topology preserves endpoint order the same way it does through
    // RedisConnectOptions, and a DISCOVER topology is never mistaken for one that does
    if (options.getType() == RedisClientType.REPLICATION) {
      return hasStaticTopology(options)
          ? RedisServerTarget.ofEndpoints(options.getEndpoints())
          : RedisServerTarget.ofUnorderedEndpoints(options.getEndpoints());
    }
    return io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0.VertxRedisServerTargets
        .of(options);
  }

  @Nullable
  public static RedisServerTarget of(@Nullable RedisConnectOptions options) {
    if (options == null) {
      return null;
    }
    if (options instanceof RedisSentinelConnectOptions) {
      return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(
          discoveryEndpoints(options.getEndpoints()),
          ((RedisSentinelConnectOptions) options).getMasterName());
    }
    if (options instanceof RedisStandaloneConnectOptions) {
      return RedisServerTarget.ofEndpoint(options.getEndpoint());
    }
    if (hasStaticTopology(options)) {
      return RedisServerTarget.ofEndpoints(options.getEndpoints());
    }
    return RedisServerTarget.ofUnorderedEndpoints(options.getEndpoints());
  }

  private static boolean hasStaticTopology(Object options) {
    try {
      Object topology = options.getClass().getMethod("getTopology").invoke(options);
      if (topology instanceof Enum<?>) {
        return ((Enum<?>) topology).name().equals("STATIC");
      }
      return topology != null && topology.toString().equals("STATIC");
    } catch (NoSuchMethodException ignored) {
      return false;
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the Vert.x Redis topology", e);
      return false;
    }
  }

  public static void pushFactoryTarget(RedisOptions options) {
    factoryTarget.set(new TargetFrame(of(options), factoryTarget.get()));
  }

  public static void popFactoryTarget() {
    TargetFrame current = factoryTarget.get();
    if (current == null || current.previous == null) {
      factoryTarget.remove();
    } else {
      factoryTarget.set(current.previous);
    }
  }

  @Nullable
  public static RedisServerTarget getFactoryTarget() {
    TargetFrame current = factoryTarget.get();
    return current == null ? null : current.target;
  }

  private static final class TargetFrame {
    @Nullable private final RedisServerTarget target;
    @Nullable private final TargetFrame previous;

    private TargetFrame(@Nullable RedisServerTarget target, @Nullable TargetFrame previous) {
      this.target = target;
      this.previous = previous;
    }
  }

  private VertxRedisServerTargets() {}
}
