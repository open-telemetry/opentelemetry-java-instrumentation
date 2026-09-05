/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbExceptionEventExtractors.setDbClientExceptionEventExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClusterInfoCache;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.util.Pool;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-4.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;
  private static final VirtualField<Connection, JedisConnectionInfo> CONNECTION_INFO =
      VirtualField.find(Connection.class, JedisConnectionInfo.class);

  private static final VirtualField<Connection, RedisServerTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, RedisServerTarget.class);
  private static final VirtualField<Connection, Boolean> CONNECTION_TARGET_SUPPRESSED =
      VirtualField.find(Connection.class, Boolean.class);

  private static final VirtualField<Pool<?>, RedisServerTarget> POOL_TARGET =
      VirtualField.find(Pool.class, RedisServerTarget.class);
  private static final VirtualField<Pool<?>, Boolean> POOL_TARGET_CONFIGURED =
      VirtualField.find(Pool.class, Boolean.class);

  private static final VirtualField<Object, ConfiguredTarget> PROVIDER_CONFIGURED_TARGET =
      getProviderConfiguredTargetVirtualField();

  private static final VirtualField<JedisClusterInfoCache, ConfiguredTarget>
      TOPOLOGY_CONFIGURED_TARGET =
          VirtualField.find(JedisClusterInfoCache.class, ConfiguredTarget.class);

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
            .addOperationMetrics(DbClientMetrics.get());
    setDbClientExceptionEventExtractor(builder);

    instrumenter = builder.buildInstrumenter(SpanKindExtractor.alwaysClient());
  }

  public static Instrumenter<JedisRequest, Void> instrumenter() {
    return instrumenter;
  }

  @Nullable
  static JedisConnectionInfo connectionInfo(Connection connection) {
    return CONNECTION_INFO.get(connection);
  }

  public static void setConnectionInfo(
      Connection connection, JedisSocketFactory socketFactory, @Nullable Object clientConfig) {
    JedisConnectionInfo connectionInfo = JedisConnectionInfo.create(socketFactory, clientConfig);
    CONNECTION_INFO.set(connection, connectionInfo);
    setConnectionTarget(connection, connectionInfo.getServerTarget());
  }

  public static void setPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    POOL_TARGET.set(pool, target);
    POOL_TARGET_CONFIGURED.set(pool, true);
  }

  public static void setProviderTarget(Object provider, @Nullable RedisServerTarget target) {
    PROVIDER_CONFIGURED_TARGET.set(provider, new ConfiguredTarget(target));
  }

  public static void setTopologyTarget(
      @Nullable JedisClusterInfoCache topologyOwner, @Nullable RedisServerTarget target) {
    if (topologyOwner == null) {
      return;
    }
    TOPOLOGY_CONFIGURED_TARGET.set(topologyOwner, new ConfiguredTarget(target));
  }

  public static void setTopologyTargetFromNodes(
      JedisClusterInfoCache topologyOwner, Collection<?> startNodes) {
    setTopologyTarget(topologyOwner, JedisServerTargets.ofNodes(startNodes));
  }

  public static void attachPoolTarget(Pool<?> pool, @Nullable Object resource) {
    if (!Boolean.TRUE.equals(POOL_TARGET_CONFIGURED.get(pool))) {
      return;
    }
    Connection connection;
    if (resource instanceof Jedis) {
      connection = ((Jedis) resource).getConnection();
    } else if (resource instanceof Connection) {
      connection = (Connection) resource;
    } else {
      return;
    }
    setConnectionTarget(connection, POOL_TARGET.get(pool));
  }

  public static void attachProviderTarget(Object provider, @Nullable Connection connection) {
    ConfiguredTarget configuredTarget = PROVIDER_CONFIGURED_TARGET.get(provider);
    if (configuredTarget != null) {
      setConnectionTarget(connection, configuredTarget.target);
    }
  }

  public static void attachProviderTargetToPools(
      Object provider, @Nullable Map<?, ? extends Pool<?>> pools) {
    ConfiguredTarget configuredTarget = PROVIDER_CONFIGURED_TARGET.get(provider);
    if (configuredTarget == null || pools == null) {
      return;
    }
    for (Pool<?> pool : pools.values()) {
      setPoolTarget(pool, configuredTarget.target);
    }
  }

  @Nullable
  public static Scope openProviderTargetScope(Object provider) {
    ConfiguredTarget configuredTarget = PROVIDER_CONFIGURED_TARGET.get(provider);
    return configuredTarget != null ? openConfiguredTargetScope(configuredTarget.target) : null;
  }

  @Nullable
  public static Scope openTopologyTargetScope(JedisClusterInfoCache topologyOwner) {
    ConfiguredTarget configuredTarget = TOPOLOGY_CONFIGURED_TARGET.get(topologyOwner);
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

  @NoMuzzle // the carrier interface was renamed after the beta release
  @SuppressWarnings("unchecked")
  private static VirtualField<Object, ConfiguredTarget> getProviderConfiguredTargetVirtualField() {
    ClassLoader classLoader = JedisSingletons.class.getClassLoader();
    Class<?> providerClass;
    try {
      providerClass =
          Class.forName("redis.clients.jedis.providers.ConnectionProvider", false, classLoader);
    } catch (ClassNotFoundException ignored) {
      try {
        providerClass =
            Class.forName(
                "redis.clients.jedis.providers.JedisConnectionProvider", false, classLoader);
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("Jedis connection provider interface not found", e);
      }
    }
    return (VirtualField<Object, ConfiguredTarget>)
        VirtualField.find(providerClass, ConfiguredTarget.class);
  }

  private static void setConnectionTarget(
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
