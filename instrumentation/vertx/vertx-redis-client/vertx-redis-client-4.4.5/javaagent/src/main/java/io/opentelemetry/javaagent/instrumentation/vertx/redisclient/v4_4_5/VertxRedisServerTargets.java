/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_4_5;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.net.SocketAddress;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisConnectOptions;
import io.vertx.redis.client.RedisOptions;
import io.vertx.redis.client.RedisSentinelConnectOptions;
import io.vertx.redis.client.RedisStandaloneConnectOptions;
import io.vertx.redis.client.impl.RedisStandaloneConnection;
import io.vertx.redis.client.impl.RedisURI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class VertxRedisServerTargets {

  private static final Logger logger = Logger.getLogger(VertxRedisServerTargets.class.getName());

  private static final VirtualField<RedisURI, RedisServerTarget> TARGET_FIELD =
      VirtualField.find(RedisURI.class, RedisServerTarget.class);
  private static final VirtualField<RedisStandaloneConnection, RedisServerTarget>
      CONNECTION_TARGET_FIELD =
          VirtualField.find(RedisStandaloneConnection.class, RedisServerTarget.class);
  private static final VirtualField<RedisConnectOptions, RedisServerTarget> OPTIONS_TARGET_FIELD =
      VirtualField.find(RedisConnectOptions.class, RedisServerTarget.class);
  private static final VirtualField<Supplier<?>, RedisServerTarget> SUPPLIER_TARGET_FIELD =
      VirtualField.find(Supplier.class, RedisServerTarget.class);
  private static final ThreadLocal<FactoryTarget> factoryTarget = new ThreadLocal<>();

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
    if (options.getType() == RedisClientType.REPLICATION && hasStaticTopology(options)) {
      return RedisServerTarget.ofEndpoints(options.getEndpoints());
    }
    return RedisServerTarget.ofUnorderedEndpoints(options.getEndpoints());
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

  private static List<String> discoveryEndpoints(List<String> connectionStrings) {
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
    factoryTarget.set(new FactoryTarget(of(options), factoryTarget.get()));
  }

  public static void popFactoryTarget() {
    FactoryTarget current = factoryTarget.get();
    if (current == null || current.previous == null) {
      factoryTarget.remove();
    } else {
      factoryTarget.set(current.previous);
    }
  }

  public static void capture(RedisConnectOptions options) {
    OPTIONS_TARGET_FIELD.set(options, of(options));
  }

  public static void capture(Supplier<?> optionsSupplier) {
    FactoryTarget current = factoryTarget.get();
    SUPPLIER_TARGET_FIELD.set(optionsSupplier, current == null ? null : current.target);
  }

  @Nullable
  public static RedisServerTarget get(RedisConnectOptions options) {
    return OPTIONS_TARGET_FIELD.get(options);
  }

  @Nullable
  public static RedisServerTarget get(Supplier<?> optionsSupplier) {
    return SUPPLIER_TARGET_FIELD.get(optionsSupplier);
  }

  public static void set(RedisURI redisUri, @Nullable RedisServerTarget target) {
    TARGET_FIELD.set(redisUri, target);
  }

  public static void setConnectionTarget(RedisStandaloneConnection connection, RedisURI redisUri) {
    RedisServerTarget target = TARGET_FIELD.get(redisUri);
    if (target != null) {
      CONNECTION_TARGET_FIELD.set(connection, target);
    }
  }

  private static final class FactoryTarget {
    @Nullable private final RedisServerTarget target;
    @Nullable private final FactoryTarget previous;

    private FactoryTarget(@Nullable RedisServerTarget target, @Nullable FactoryTarget previous) {
      this.target = target;
      this.previous = previous;
    }
  }

  private VertxRedisServerTargets() {}
}
