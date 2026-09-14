/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Collection;
import javax.annotation.Nullable;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClusterInfoCache;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.providers.ConnectionProvider;
import redis.clients.jedis.util.Pool;

public class JedisConfiguredTargets {
  // The cache constructor records nodes before its refresh-task constructor receives the cache.
  private static final ThreadLocal<Collection<?>> PENDING_TOPOLOGY_NODES = new ThreadLocal<>();

  private static final VirtualField<JedisSocketFactory, ConfiguredTarget>
      SOCKET_FACTORY_CONFIGURED_TARGET =
          VirtualField.find(JedisSocketFactory.class, ConfiguredTarget.class);

  private static final VirtualField<Pool<?>, ConfiguredTarget> SENTINEL_POOL_CONFIGURED_TARGET =
      VirtualField.find(Pool.class, ConfiguredTarget.class);

  private static final VirtualField<ConnectionProvider, ConfiguredTarget>
      PROVIDER_CONFIGURED_TARGET =
          VirtualField.find(ConnectionProvider.class, ConfiguredTarget.class);

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

  public static void setProviderTarget(
      ConnectionProvider provider, @Nullable RedisServerTarget target) {
    PROVIDER_CONFIGURED_TARGET.set(provider, ConfiguredTarget.create(target));
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
    setTopologyTarget(topologyOwner, JedisServerTarget.ofNodes(startNodes));
  }

  public static void beginTopologyTargetInitialization(Collection<?> startNodes) {
    PENDING_TOPOLOGY_NODES.set(startNodes);
  }

  public static void initializePendingTopologyTarget(JedisClusterInfoCache topologyOwner) {
    try {
      Collection<?> startNodes = PENDING_TOPOLOGY_NODES.get();
      if (startNodes != null) {
        setTopologyTargetFromNodes(topologyOwner, startNodes);
      }
    } finally {
      endTopologyTargetInitialization();
    }
  }

  public static void endTopologyTargetInitialization() {
    PENDING_TOPOLOGY_NODES.remove();
  }

  @Nullable
  public static Context providerTargetContext(ConnectionProvider provider) {
    ConfiguredTarget configuredTarget = getProviderTarget(provider);
    return configuredTarget != null ? configuredTargetContext(configuredTarget) : null;
  }

  @Nullable
  public static Context topologyTargetContext(JedisClusterInfoCache topologyOwner) {
    ConfiguredTarget configuredTarget = TOPOLOGY_CONFIGURED_TARGET.get(topologyOwner);
    return configuredTarget != null ? configuredTargetContext(configuredTarget) : null;
  }

  @Nullable
  public static Context sentinelPoolTargetContext(Pool<?> pool) {
    ConfiguredTarget configuredTarget = SENTINEL_POOL_CONFIGURED_TARGET.get(pool);
    return configuredTarget != null ? configuredTargetContext(configuredTarget) : null;
  }

  public static Context configuredTargetContext(@Nullable RedisServerTarget target) {
    return configuredTargetContext(ConfiguredTarget.create(target));
  }

  private static Context configuredTargetContext(ConfiguredTarget configuredTarget) {
    return Context.current().with(CURRENT_CONFIGURED_TARGET, configuredTarget);
  }

  @Nullable
  private static ConfiguredTarget getProviderTarget(ConnectionProvider provider) {
    return PROVIDER_CONFIGURED_TARGET.get(provider);
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
