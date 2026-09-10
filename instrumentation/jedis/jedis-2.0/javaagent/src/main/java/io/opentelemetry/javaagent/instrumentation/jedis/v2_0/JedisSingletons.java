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
import javax.annotation.Nullable;
import redis.clients.jedis.BinaryJedis;
import redis.clients.jedis.Connection;
import redis.clients.util.Pool;
import redis.clients.util.Sharded;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-2.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;

  private static final VirtualField<Connection, ConnectionTargetState> CONNECTION_TARGET_STATE =
      VirtualField.find(Connection.class, ConnectionTargetState.class);

  private static final VirtualField<Sharded<?, ?>, ShardedTargetState> SHARDED_TARGET_STATE =
      VirtualField.find(Sharded.class, ShardedTargetState.class);

  private static final VirtualField<Pool<?>, PoolTargetState> POOL_TARGET_STATE =
      VirtualField.find(Pool.class, PoolTargetState.class);

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
    SHARDED_TARGET_STATE.set(sharded, new ShardedTargetState(target));
  }

  public static void setPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    POOL_TARGET_STATE.set(pool, new PoolTargetState(target));
  }

  public static void attachShardedTarget(Sharded<?, ?> sharded, @Nullable Object shard) {
    ShardedTargetState state = SHARDED_TARGET_STATE.get(sharded);
    if (state != null) {
      attach(state.target, shard);
    }
  }

  public static void attachPoolTarget(Pool<?> pool, @Nullable Object resource) {
    PoolTargetState state = POOL_TARGET_STATE.get(pool);
    if (state != null) {
      attach(state.target, resource);
    }
  }

  @Nullable
  public static ConfiguredTargetScope openPoolTargetScope(Pool<?> pool) {
    PoolTargetState state = POOL_TARGET_STATE.get(pool);
    return state != null ? openConfiguredTargetScope(state.target) : null;
  }

  public static ConfiguredTargetScope openConfiguredTargetScope(
      @Nullable RedisServerTarget target) {
    Scope scope =
        Context.current()
            .with(CURRENT_CONFIGURED_TARGET, new ConfiguredTarget(target))
            .makeCurrent();
    return new ConfiguredTargetScope(target, scope);
  }

  static void attach(@Nullable RedisServerTarget target, @Nullable Object jedis) {
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
    CONNECTION_TARGET_STATE.set(connection, new ConnectionTargetState(target));
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
    ConfiguredTarget configuredTarget = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (configuredTarget != null) {
      return configuredTarget.target;
    }
    ConnectionTargetState state = CONNECTION_TARGET_STATE.get(connection);
    return state != null ? state.target : null;
  }

  private JedisSingletons() {}

  static final class ConfiguredTarget {
    @Nullable private final RedisServerTarget target;

    private ConfiguredTarget(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }

  public static final class ConfiguredTargetScope implements AutoCloseable {
    @Nullable private final RedisServerTarget target;
    private final Scope scope;

    private ConfiguredTargetScope(@Nullable RedisServerTarget target, Scope scope) {
      this.target = target;
      this.scope = scope;
    }

    @Nullable
    public RedisServerTarget getTarget() {
      return target;
    }

    @Override
    public void close() {
      scope.close();
    }
  }

  private static final class ConnectionTargetState {
    @Nullable private final RedisServerTarget target;

    private ConnectionTargetState(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }

  private static final class ShardedTargetState {
    @Nullable private final RedisServerTarget target;

    private ShardedTargetState(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }

  private static final class PoolTargetState {
    @Nullable private final RedisServerTarget target;

    private PoolTargetState(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }
}
