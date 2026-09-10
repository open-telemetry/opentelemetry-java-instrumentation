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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClusterInfoCache;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.util.Pool;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-4.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;
  private static final VirtualField<Connection, JedisConnectionInfo> CONNECTION_INFO =
      VirtualField.find(Connection.class, JedisConnectionInfo.class);

  private static final VirtualField<Connection, ConfiguredTarget> CONNECTION_CONFIGURED_TARGET =
      VirtualField.find(Connection.class, ConfiguredTarget.class);

  private static final VirtualField<Pool<?>, ConfiguredTarget> POOL_CONFIGURED_TARGET =
      VirtualField.find(Pool.class, ConfiguredTarget.class);

  @Nullable
  private static final VirtualField<Object, ConfiguredTarget> PROVIDER_CONFIGURED_TARGET =
      createProviderConfiguredTargetField();

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
    POOL_CONFIGURED_TARGET.set(pool, new ConfiguredTarget(target));
  }

  public static void setProviderTarget(Object provider, @Nullable RedisServerTarget target) {
    if (PROVIDER_CONFIGURED_TARGET != null) {
      PROVIDER_CONFIGURED_TARGET.set(provider, new ConfiguredTarget(target));
    }
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
    setTopologyTarget(topologyOwner, targetOfNodes(startNodes));
  }

  public static void attachPoolTarget(Pool<?> pool, @Nullable Object resource) {
    ConfiguredTarget configuredTarget = POOL_CONFIGURED_TARGET.get(pool);
    if (configuredTarget == null) {
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
    setConnectionTarget(connection, configuredTarget.target);
  }

  public static void attachProviderTarget(Object provider, @Nullable Connection connection) {
    ConfiguredTarget configuredTarget = getProviderTarget(provider);
    if (configuredTarget != null) {
      setConnectionTarget(connection, configuredTarget.target);
    }
  }

  public static void attachProviderTargetToPools(
      Object provider, @Nullable Map<?, ? extends Pool<?>> pools) {
    ConfiguredTarget configuredTarget =
        provider instanceof JedisClusterInfoCache
            ? TOPOLOGY_CONFIGURED_TARGET.get((JedisClusterInfoCache) provider)
            : getProviderTarget(provider);
    if (configuredTarget == null || pools == null) {
      return;
    }
    for (Pool<?> pool : pools.values()) {
      setPoolTarget(pool, configuredTarget.target);
    }
  }

  @Nullable
  public static Scope openProviderTargetScope(Object provider) {
    ConfiguredTarget configuredTarget = getProviderTarget(provider);
    return configuredTarget != null ? openConfiguredTargetScope(configuredTarget.target) : null;
  }

  @Nullable
  public static Scope openTopologyTargetScope(JedisClusterInfoCache topologyOwner) {
    ConfiguredTarget configuredTarget = TOPOLOGY_CONFIGURED_TARGET.get(topologyOwner);
    return configuredTarget != null ? openConfiguredTargetScope(configuredTarget.target) : null;
  }

  @Nullable
  public static Scope openPoolTargetScope(Pool<?> pool) {
    ConfiguredTarget configuredTarget = POOL_CONFIGURED_TARGET.get(pool);
    return configuredTarget != null ? openConfiguredTargetScope(configuredTarget.target) : null;
  }

  public static Scope openConfiguredTargetScope(@Nullable RedisServerTarget target) {
    return openConfiguredTargetScope(new ConfiguredTarget(target));
  }

  private static Scope openConfiguredTargetScope(ConfiguredTarget configuredTarget) {
    return Context.current().with(CURRENT_CONFIGURED_TARGET, configuredTarget).makeCurrent();
  }

  @Nullable
  private static ConfiguredTarget getProviderTarget(Object provider) {
    return PROVIDER_CONFIGURED_TARGET == null ? null : PROVIDER_CONFIGURED_TARGET.get(provider);
  }

  @Nullable
  private static VirtualField<Object, ConfiguredTarget> createProviderConfiguredTargetField() {
    ClassLoader classLoader = JedisSingletons.class.getClassLoader();
    try {
      return providerConfiguredTargetField(
          Class.forName("redis.clients.jedis.providers.ConnectionProvider", false, classLoader));
    } catch (ClassNotFoundException ignored) {
      try {
        return providerConfiguredTargetField(
            Class.forName(
                "redis.clients.jedis.providers.JedisConnectionProvider", false, classLoader));
      } catch (ClassNotFoundException ignore) {
        return null;
      }
    }
  }

  @NoMuzzle // the carrier interface was renamed after the beta release
  @SuppressWarnings("unchecked") // the carrier type is not known at compile time
  private static VirtualField<Object, ConfiguredTarget> providerConfiguredTargetField(
      Class<?> providerClass) {
    return (VirtualField<Object, ConfiguredTarget>)
        VirtualField.find(providerClass, ConfiguredTarget.class);
  }

  private static void setConnectionTarget(
      @Nullable Connection connection, @Nullable RedisServerTarget target) {
    if (connection == null) {
      return;
    }
    CONNECTION_CONFIGURED_TARGET.set(connection, new ConfiguredTarget(target));
  }

  @Nullable
  static RedisServerTarget connectionTarget(Connection connection) {
    ConfiguredTarget configuredTarget = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (configuredTarget != null) {
      return configuredTarget.target;
    }
    configuredTarget = CONNECTION_CONFIGURED_TARGET.get(connection);
    return configuredTarget != null ? configuredTarget.target : null;
  }

  @Nullable
  public static RedisServerTarget targetOfNodes(@Nullable Collection<?> nodes) {
    return RedisServerTarget.ofUnorderedEndpoints(endpointStrings(nodes));
  }

  @Nullable
  public static RedisServerTarget targetOfShards(@Nullable List<HostAndPort> shards) {
    return RedisServerTarget.ofEndpoints(endpointStrings(shards));
  }

  @Nullable
  public static RedisServerTarget targetOfSentinels(
      @Nullable String masterName, @Nullable Collection<?> sentinels) {
    List<String> endpoints = null;
    if (sentinels != null) {
      endpoints = new ArrayList<>(sentinels.size());
      for (Object sentinel : sentinels) {
        if (sentinel instanceof HostAndPort) {
          HostAndPort hostAndPort = (HostAndPort) sentinel;
          endpoints.add(RedisServerTarget.endpoint(hostAndPort.getHost(), hostAndPort.getPort()));
        } else if (sentinel instanceof String) {
          endpoints.add(RedisServerTarget.normalizeHostAndPort((String) sentinel));
        } else {
          endpoints.add(null);
        }
      }
    }
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(endpoints, masterName);
  }

  @Nullable
  public static RedisServerTarget targetOfSentinelsFromArguments(
      @Nullable String masterName, @Nullable Object[] arguments) {
    if (arguments != null) {
      for (Object argument : arguments) {
        if (argument instanceof Collection) {
          return targetOfSentinels(masterName, (Collection<?>) argument);
        }
      }
    }
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(null, masterName);
  }

  @Nullable
  private static List<String> endpointStrings(@Nullable Collection<?> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(nodes.size());
    for (Object value : nodes) {
      if (value instanceof HostAndPort) {
        HostAndPort node = (HostAndPort) value;
        endpoints.add(RedisServerTarget.endpoint(node.getHost(), node.getPort()));
      } else {
        endpoints.add(null);
      }
    }
    return endpoints;
  }

  private JedisSingletons() {}

  static final class ConfiguredTarget {
    @Nullable private final RedisServerTarget target;

    private ConfiguredTarget(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }
}
