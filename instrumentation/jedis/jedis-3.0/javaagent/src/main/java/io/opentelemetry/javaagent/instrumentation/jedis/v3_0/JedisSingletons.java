/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

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
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.Connection;
import redis.clients.jedis.JedisClusterConnectionHandler;
import redis.clients.jedis.util.Pool;
import redis.clients.jedis.util.Sharded;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-3.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;

  private static final VirtualField<Connection, ConfiguredTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, ConfiguredTarget.class);

  private static final VirtualField<Sharded<?, ?>, ConfiguredTarget> SHARDED_TARGET =
      VirtualField.find(Sharded.class, ConfiguredTarget.class);

  private static final VirtualField<Pool<?>, ConfiguredTarget> POOL_TARGET =
      VirtualField.find(Pool.class, ConfiguredTarget.class);

  private static final VirtualField<JedisClusterConnectionHandler, ConfiguredTarget>
      CLUSTER_TARGET =
          VirtualField.find(JedisClusterConnectionHandler.class, ConfiguredTarget.class);

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

  public static Instrumenter<JedisRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static void setShardedTarget(Sharded<?, ?> sharded, @Nullable RedisServerTarget target) {
    SHARDED_TARGET.set(sharded, new ConfiguredTarget(target));
  }

  public static void setPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    POOL_TARGET.set(pool, new ConfiguredTarget(target));
  }

  @Nullable
  public static RedisServerTarget sentinelTarget(
      @Nullable String masterName, @Nullable Collection<?> sentinels) {
    return JedisServerTargets.ofSentinels(masterName, sentinels);
  }

  public static void setClusterTarget(
      JedisClusterConnectionHandler handler, @Nullable RedisServerTarget target) {
    CLUSTER_TARGET.set(handler, new ConfiguredTarget(target));
  }

  public static void attachShardedTarget(Sharded<?, ?> sharded, @Nullable Object shard) {
    ConfiguredTarget configuredTarget = SHARDED_TARGET.get(sharded);
    if (configuredTarget != null) {
      attach(configuredTarget.target, shard);
    }
  }

  public static void attachPoolTarget(Pool<?> pool, @Nullable Object resource) {
    ConfiguredTarget configuredTarget = POOL_TARGET.get(pool);
    if (configuredTarget != null) {
      attach(configuredTarget.target, resource);
    }
  }

  public static void attachClusterTarget(
      JedisClusterConnectionHandler handler, @Nullable Object connection) {
    ConfiguredTarget configuredTarget = CLUSTER_TARGET.get(handler);
    if (configuredTarget != null) {
      attach(configuredTarget.target, connection);
    }
  }

  public static void attachClusterTargetToPools(
      JedisClusterConnectionHandler handler, @Nullable Map<?, ?> pools) {
    ConfiguredTarget configuredTarget = CLUSTER_TARGET.get(handler);
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
  public static Scope openClusterTargetScope(JedisClusterConnectionHandler handler) {
    ConfiguredTarget configuredTarget = CLUSTER_TARGET.get(handler);
    return configuredTarget != null ? openConfiguredTargetScope(configuredTarget.target) : null;
  }

  @Nullable
  public static Scope openPoolTargetScope(Pool<?> pool) {
    ConfiguredTarget configuredTarget = POOL_TARGET.get(pool);
    return configuredTarget != null ? openConfiguredTargetScope(configuredTarget.target) : null;
  }

  public static Scope openConfiguredTargetScope(@Nullable RedisServerTarget target) {
    return Context.current()
        .with(CURRENT_CONFIGURED_TARGET, new ConfiguredTarget(target))
        .makeCurrent();
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
    CONNECTION_TARGET.set(connection, new ConfiguredTarget(target));
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
    ConfiguredTarget configuredTarget = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (configuredTarget != null) {
      return configuredTarget.target;
    }
    configuredTarget = CONNECTION_TARGET.get(connection);
    return configuredTarget == null ? null : configuredTarget.target;
  }

  private JedisSingletons() {}

  private static final class ConfiguredTarget {
    @Nullable private final RedisServerTarget target;

    private ConfiguredTarget(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }
}
