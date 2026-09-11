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
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClusterInfoCache;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.util.Pool;

public class JedisSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.jedis-4.0";

  private static final Instrumenter<JedisRequest, Void> instrumenter;
  private static final VirtualField<Connection, JedisConnectionInfo> CONNECTION_INFO =
      VirtualField.find(Connection.class, JedisConnectionInfo.class);

  private static final VirtualField<Pool<?>, ConfiguredTarget> SENTINEL_POOL_CONFIGURED_TARGET =
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
    CONNECTION_INFO.set(connection, JedisConnectionInfo.create(socketFactory, clientConfig));
  }

  public static void setSentinelPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    SENTINEL_POOL_CONFIGURED_TARGET.set(pool, ConfiguredTarget.create(target));
  }

  public static void setProviderTarget(Object provider, @Nullable RedisServerTarget target) {
    if (PROVIDER_CONFIGURED_TARGET != null) {
      PROVIDER_CONFIGURED_TARGET.set(provider, ConfiguredTarget.create(target));
    }
  }

  public static void setTopologyTarget(
      @Nullable JedisClusterInfoCache topologyOwner, @Nullable RedisServerTarget target) {
    if (topologyOwner == null) {
      return;
    }
    TOPOLOGY_CONFIGURED_TARGET.set(topologyOwner, ConfiguredTarget.create(target));
  }

  public static void setTopologyTargetFromNodes(
      JedisClusterInfoCache topologyOwner, Collection<?> startNodes) {
    setTopologyTarget(topologyOwner, targetOfNodes(startNodes));
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
  public static Scope openSentinelPoolTargetScope(Pool<?> pool) {
    ConfiguredTarget configuredTarget = SENTINEL_POOL_CONFIGURED_TARGET.get(pool);
    return configuredTarget != null ? openConfiguredTargetScope(configuredTarget.target) : null;
  }

  public static Scope openConfiguredTargetScope(@Nullable RedisServerTarget target) {
    return openConfiguredTargetScope(ConfiguredTarget.create(target));
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

  @Nullable
  static RedisServerTarget currentOrDirectTarget(HostAndPort hostAndPort) {
    ConfiguredTarget configuredTarget = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (configuredTarget != null) {
      return configuredTarget.target;
    }
    return RedisServerTarget.ofHostAndPort(hostAndPort.getHost(), hostAndPort.getPort());
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
    private static final ConfiguredTarget UNREPRESENTABLE = new ConfiguredTarget(null);

    @Nullable private final RedisServerTarget target;

    private static ConfiguredTarget create(@Nullable RedisServerTarget target) {
      return target == null ? UNREPRESENTABLE : new ConfiguredTarget(target);
    }

    private ConfiguredTarget(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }
}
