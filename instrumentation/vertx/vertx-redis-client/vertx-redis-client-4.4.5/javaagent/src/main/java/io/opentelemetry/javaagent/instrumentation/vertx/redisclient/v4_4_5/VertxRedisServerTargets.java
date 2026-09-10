/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import static io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0.VertxRedisServerTargets.discoveryEndpoints;
import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.vertx.core.Future;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.RedisSentinelConnectOptions;
import io.vertx.redis.client.RedisStandaloneConnectOptions;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class VertxRedisServerTargets {

  private static final Logger logger = Logger.getLogger(VertxRedisServerTargets.class.getName());

  private static final String CONSTANT_SUPPLIER_CLASS_NAME =
      "io.vertx.redis.client.ConstantSupplier";

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

  @Nullable
  public static RedisServerTarget ofConstantSupplier(
      Object manager, @Nullable Supplier<?> optionsSupplier) {
    if (optionsSupplier == null) {
      return null;
    }
    Class<?> supplierClass = optionsSupplier.getClass();
    try {
      // Vert.x 5 legacy clients use this exact constant supplier; other suppliers may be dynamic.
      Class<?> constantSupplierClass =
          Class.forName(CONSTANT_SUPPLIER_CLASS_NAME, false, manager.getClass().getClassLoader());
      if (supplierClass != constantSupplierClass) {
        return null;
      }
    } catch (ClassNotFoundException ignored) {
      return null;
    }

    Object supplied = optionsSupplier.get();
    if (!(supplied instanceof Future)) {
      return null;
    }
    Future<?> optionsFuture = (Future<?>) supplied;
    Object options = optionsFuture.succeeded() ? optionsFuture.result() : null;
    return options instanceof RedisConnectOptions ? of((RedisConnectOptions) options) : null;
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

  private VertxRedisServerTargets() {}
}
