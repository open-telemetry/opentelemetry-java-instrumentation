/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.util.Collection;
import javax.annotation.Nullable;
import redis.clients.jedis.Connection;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClusterConnectionHandler;
import redis.clients.jedis.util.Pool;

public class JedisConfiguredTargets {

  private static final VirtualField<Connection, ConfiguredTarget> CONNECTION_TARGET =
      VirtualField.find(Connection.class, ConfiguredTarget.class);

  private static final VirtualField<Pool<?>, ConfiguredTarget> POOL_TARGET =
      VirtualField.find(Pool.class, ConfiguredTarget.class);

  private static final VirtualField<HostAndPort, OriginalEndpoint> ORIGINAL_ENDPOINT =
      VirtualField.find(HostAndPort.class, OriginalEndpoint.class);

  private static final VirtualField<JedisClusterConnectionHandler, ConfiguredTarget>
      CLUSTER_TARGET =
          VirtualField.find(JedisClusterConnectionHandler.class, ConfiguredTarget.class);

  private static final ContextKey<ConfiguredTarget> CURRENT_CONFIGURED_TARGET =
      ContextKey.named("opentelemetry-jedis-configured-target");

  public static void setPoolTarget(Pool<?> pool, @Nullable RedisServerTarget target) {
    POOL_TARGET.set(pool, new ConfiguredTarget(target));
  }

  public static void capturePoolTarget(Pool<?> pool) {
    ConfiguredTarget configuredTarget = Context.current().get(CURRENT_CONFIGURED_TARGET);
    if (configuredTarget != null) {
      POOL_TARGET.set(pool, configuredTarget);
    }
  }

  public static void captureOriginalEndpoint(
      @Nullable HostAndPort parsedEndpoint, @Nullable String configuredEndpoint) {
    if (parsedEndpoint != null && configuredEndpoint != null) {
      ORIGINAL_ENDPOINT.set(parsedEndpoint, new OriginalEndpoint(configuredEndpoint));
    }
  }

  static String sentinelEndpoint(HostAndPort sentinel) {
    OriginalEndpoint originalEndpoint = ORIGINAL_ENDPOINT.get(sentinel);
    return originalEndpoint == null
        ? RedisServerTarget.endpoint(sentinel.getHost(), sentinel.getPort())
        : RedisServerTarget.normalizeHostAndPort(originalEndpoint.value);
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

  public static void setConnectionTarget(
      @Nullable Connection connection, @Nullable RedisServerTarget target) {
    if (connection == null) {
      return;
    }
    CONNECTION_TARGET.set(connection, new ConfiguredTarget(target));
  }

  public static void captureConnectionTarget(
      Connection connection, @Nullable RedisServerTarget fallbackTarget) {
    ConfiguredTarget configuredTarget = Context.current().get(CURRENT_CONFIGURED_TARGET);
    setConnectionTarget(
        connection, configuredTarget == null ? fallbackTarget : configuredTarget.target);
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

  private JedisConfiguredTargets() {}

  private static final class ConfiguredTarget {
    @Nullable private final RedisServerTarget target;

    private ConfiguredTarget(@Nullable RedisServerTarget target) {
      this.target = target;
    }
  }

  private static final class OriginalEndpoint {
    private final String value;

    private OriginalEndpoint(String value) {
      this.value = value;
    }
  }
}
