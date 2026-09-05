/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.service.peer.ServicePeerAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.Map;
import javax.annotation.Nullable;
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.Connection;
import redis.clients.util.Pool;
import redis.clients.util.Sharded;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-2.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;

  private static final VirtualField<Connection, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, RedisServerTarget.class);
  private static final VirtualField<Connection, Boolean> CONNECTION_TARGET_SUPPRESSED =
      VirtualField.find(Connection.class, Boolean.class);

  private static final VirtualField<Sharded<?, ?>, RedisServerTarget> SHARDED_TARGET =
      VirtualField.find(Sharded.class, RedisServerTarget.class);
  private static final VirtualField<Sharded<?, ?>, Boolean> SHARDED_TARGET_CONFIGURED =
      VirtualField.find(Sharded.class, Boolean.class);

  private static final VirtualField<Pool<?>, RedisServerTarget> POOL_TARGET =
      VirtualField.find(Pool.class, RedisServerTarget.class);
  private static final VirtualField<Pool<?>, Boolean> POOL_TARGET_CONFIGURED =
      VirtualField.find(Pool.class, Boolean.class);

  @Nullable
  private static final VirtualField<Object, ConfiguredTarget> CLUSTER_CONFIGURED_TARGET =
      getClusterConfiguredTargetVirtualField();

  private static final ContextKey<ConfiguredTarget> CURRENT_CONFIGURED_TARGET =
      ContextKey.named("opentelemetry-jedis-configured-target");

  static {
    JedisDbAttributesGetter dbAttributesGetter = new JedisDbAttributesGetter();
    // Redis semantic conventions don't follow the regular pattern of adding db.namespace to the
    // span name.
    JedisDbAttributesGetter spanNameAttributesGetter =
        new JedisDbAttributesGetter() {
          @Override
          @Nullable
          public String getDbNamespace(JedisRequest request) {
            return null;
          }
        };

    InstrumenterBuilder<JedisRequest, Void> builder =
        Instrumenter.<JedisRequest, Void>builder(
                GlobalOpenTelemetry.get(),
                INSTRUMENTATION_NAME,
                DbClientSpanNameExtractor.create(spanNameAttributesGetter))
            .addAttributesExtractor(DbClientAttributesExtractor.create(dbAttributesGetter))
            .addAttributesExtractor(
                ServicePeerAttributesExtractor.create(
                    dbAttributesGetter, GlobalOpenTelemetry.get()))
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  static Instrumenter<JedisRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static void setShardedTarget(Sharded<?, ?> sharded, @Nullable RedisServerTarget target) {
    SHARDED_TARGET.set(sharded, target);
    SHARDED_TARGET_CONFIGURED.set(sharded, true);
  }

  public static void setPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    POOL_TARGET.set(pool, target);
    POOL_TARGET_CONFIGURED.set(pool, true);
  }

  public static void setClusterTarget(Object handler, @Nullable RedisServerTarget target) {
    if (CLUSTER_CONFIGURED_TARGET != null) {
      CLUSTER_CONFIGURED_TARGET.set(handler, new ConfiguredTarget(target));
    }
  }

  public static void attachShardedTarget(Sharded<?, ?> sharded, @Nullable Object shard) {
    if (Boolean.TRUE.equals(SHARDED_TARGET_CONFIGURED.get(sharded))) {
      attach(SHARDED_TARGET.get(sharded), shard);
    }
  }

  public static void attachPoolTarget(Pool<?> pool, @Nullable Object resource) {
    if (Boolean.TRUE.equals(POOL_TARGET_CONFIGURED.get(pool))) {
      attach(POOL_TARGET.get(pool), resource);
    }
  }

  public static void attachClusterTarget(Object handler, @Nullable Object connection) {
    ConfiguredTarget configuredTarget = getClusterConfiguredTarget(handler);
    if (configuredTarget != null) {
      attach(configuredTarget.target, connection);
    }
  }

  public static void attachClusterTargetToPools(Object handler, @Nullable Map<?, ?> pools) {
    ConfiguredTarget configuredTarget = getClusterConfiguredTarget(handler);
    if (configuredTarget == null || pools == null) {
      return;
    }
    for (Object pool : pools.values()) {
      if (pool instanceof Pool<?>) {
        setPoolTarget((Pool<?>) pool, configuredTarget.target);
      }
    }
  }

  @Nullable
  public static Scope openClusterTargetScope(Object handler) {
    ConfiguredTarget configuredTarget = getClusterConfiguredTarget(handler);
    return configuredTarget != null ? openConfiguredTargetScope(configuredTarget.target) : null;
  }

  @Nullable
  public static Scope openPoolTargetScope(Pool<?> pool) {
    return Boolean.TRUE.equals(POOL_TARGET_CONFIGURED.get(pool))
        ? openConfiguredTargetScope(POOL_TARGET.get(pool))
        : null;
  }

  public static Scope openConfiguredTargetScope(@Nullable RedisServerTarget target) {
    return Context.current()
        .with(CURRENT_CONFIGURED_TARGET, new ConfiguredTarget(target))
        .makeCurrent();
  }

  @NoMuzzle // the carrier class was added after the beginning of this module's version range
  @SuppressWarnings("unchecked")
  @Nullable
  private static VirtualField<Object, ConfiguredTarget> getClusterConfiguredTargetVirtualField() {
    try {
      Class<?> handlerClass =
          Class.forName(
              "redis.clients.jedis.JedisClusterConnectionHandler",
              false,
              JedisSingletons.class.getClassLoader());
      return (VirtualField<Object, ConfiguredTarget>)
          VirtualField.find(handlerClass, ConfiguredTarget.class);
    } catch (ClassNotFoundException ignored) {
      return null;
    }
  }

  @Nullable
  private static ConfiguredTarget getClusterConfiguredTarget(Object handler) {
    return CLUSTER_CONFIGURED_TARGET != null ? CLUSTER_CONFIGURED_TARGET.get(handler) : null;
  }

  private static void attach(@Nullable RedisServerTarget target, @Nullable Object jedis) {
    if (!(jedis instanceof BinaryJedis)) {
      return;
    }
    Connection connection = ((BinaryJedis) jedis).getClient();
    setConnectionTarget(connection, target);
  }

  public static void setConnectionTarget(
      @Nullable Connection connection, @Nullable RedisServerTarget target) {
    if (connection == null) {
      return;
    }
    if (target != null) {
      CONNECTION_TARGET.set(connection, target);
      CONNECTION_TARGET_SUPPRESSED.set(connection, null);
    } else {
      CONNECTION_TARGET_SUPPRESSED.set(connection, true);
    }
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
    ConfiguredTarget configuredTarget = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (configuredTarget != null) {
      return configuredTarget.target;
    }
    if (Boolean.TRUE.equals(CONNECTION_TARGET_SUPPRESSED.get(connection))) {
      return null;
    }
    return CONNECTION_TARGET.get(connection);
  }

  private JedisSingletons() {}

  static final class ConfiguredTarget {
    @Nullable private final RedisServerTarget target;

    private ConfiguredTarget(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }
}
