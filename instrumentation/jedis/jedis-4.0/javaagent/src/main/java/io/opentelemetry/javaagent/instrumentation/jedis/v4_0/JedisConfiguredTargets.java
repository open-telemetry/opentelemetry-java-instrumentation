/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.tooling.muzzle.NoMuzzle;
import java.util.Collection;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClusterInfoCache;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.util.Pool;

public class JedisConfiguredTargets {
  private static final VirtualField<JedisSocketFactory, ConfiguredTarget>
      SOCKET_FACTORY_CONFIGURED_TARGET =
          VirtualField.find(JedisSocketFactory.class, ConfiguredTarget.class);

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

  public static void setSocketFactoryTarget(
      JedisSocketFactory socketFactory, HostAndPort hostAndPort) {
    SOCKET_FACTORY_CONFIGURED_TARGET.set(
        socketFactory, ConfiguredTarget.create(currentOrDirectTarget(hostAndPort)));
  }

  @Nullable
  public static RedisServerTarget socketFactoryTarget(@Nullable JedisSocketFactory socketFactory) {
    ConfiguredTarget configuredTarget =
        socketFactory == null ? null : SOCKET_FACTORY_CONFIGURED_TARGET.get(socketFactory);
    return configuredTarget == null ? null : configuredTarget.target;
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
    setTopologyTarget(topologyOwner, JedisSingletons.targetOfNodes(startNodes));
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
    ClassLoader classLoader = JedisConfiguredTargets.class.getClassLoader();
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
  private static RedisServerTarget currentOrDirectTarget(HostAndPort hostAndPort) {
    ConfiguredTarget configuredTarget = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (configuredTarget != null) {
      return configuredTarget.target;
    }
    return RedisServerTarget.ofHostAndPort(hostAndPort.getHost(), hostAndPort.getPort());
  }

  private JedisConfiguredTargets() {}

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
