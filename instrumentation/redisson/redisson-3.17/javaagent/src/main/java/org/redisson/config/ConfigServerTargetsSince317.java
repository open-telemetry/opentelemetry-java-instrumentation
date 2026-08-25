/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.redisson.config;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import javax.annotation.Nullable;

// This helper is in the Redisson package in order to read the per mode configuration, which Config
// only exposes to its own package.
public final class ConfigServerTargetsSince317 {

  private static final Logger logger =
      Logger.getLogger(ConfigServerTargetsSince317.class.getName());

  @Nullable private static final MethodHandle SERVICE_MANAGER_GET_CFG = findServiceManagerGetCfg();

  @Nullable
  private static MethodHandle findServiceManagerGetCfg() {
    try {
      Class<?> serviceManagerClass =
          Class.forName(
              "org.redisson.connection.ServiceManager",
              false,
              ConfigServerTargetsSince317.class.getClassLoader());
      return MethodHandles.publicLookup()
          .findVirtual(serviceManagerClass, "getCfg", MethodType.methodType(Config.class));
    } catch (ReflectiveOperationException e) {
      // redisson only routes the configuration through a service manager between 3.20 and 3.29
      return null;
    }
  }

  /**
   * The target the configuration names, which is the Sentinel endpoints scoped by their master, or
   * the configured nodes when the client was configured against a cluster or a replicated set.
   *
   * <p>A client configured with a single address needs no target of its own: the address the
   * connection reports is already the address it was configured with.
   */
  @Nullable
  public static RedisServerTarget of(@Nullable Config config) {
    if (config == null) {
      return null;
    }
    SentinelServersConfig sentinelConfig = config.getSentinelServersConfig();
    if (sentinelConfig != null) {
      return ofAddresses(sentinelConfig.getSentinelAddresses(), sentinelConfig.getMasterName());
    }
    ClusterServersConfig clusterConfig = config.getClusterServersConfig();
    if (clusterConfig != null) {
      return ofAddresses(clusterConfig.getNodeAddresses());
    }
    ReplicatedServersConfig replicatedConfig = config.getReplicatedServersConfig();
    if (replicatedConfig != null) {
      return ofAddresses(replicatedConfig.getNodeAddresses());
    }
    MasterSlaveServersConfig masterSlaveConfig = config.getMasterSlaveServersConfig();
    if (masterSlaveConfig != null) {
      return ofAddresses(
          masterSlaveConfig.getMasterAddress(), masterSlaveConfig.getSlaveAddresses());
    }
    return null;
  }

  /**
   * The target of a connection manager that was handed the service manager the configuration lives
   * in, which is how redisson 3.20 through 3.29 build their connection managers.
   */
  @Nullable
  public static RedisServerTarget ofServiceManager(@Nullable Object serviceManager) {
    if (serviceManager == null || SERVICE_MANAGER_GET_CFG == null) {
      return null;
    }
    try {
      return of((Config) SERVICE_MANAGER_GET_CFG.invoke(serviceManager));
    } catch (Throwable t) {
      logger.log(FINE, "Failed to read the Redisson configuration from the service manager", t);
      return null;
    }
  }

  @Nullable
  private static RedisServerTarget ofAddresses(@Nullable Collection<String> addresses) {
    if (addresses == null || addresses.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(addresses);
    Collections.sort(endpoints);
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofAddresses(
      @Nullable String firstAddress, @Nullable Collection<String> otherAddresses) {
    List<String> endpoints = new ArrayList<>();
    if (firstAddress != null) {
      endpoints.add(firstAddress);
    }
    Set<String> sortedAddresses = new TreeSet<>();
    if (otherAddresses != null) {
      for (String address : otherAddresses) {
        if (address != null) {
          sortedAddresses.add(address);
        }
      }
    }
    endpoints.addAll(sortedAddresses);
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofAddresses(
      @Nullable Collection<String> addresses, @Nullable String logicalName) {
    if (addresses == null || addresses.isEmpty()) {
      return RedisServerTarget.ofEndpointsAndLogicalName(null, logicalName);
    }
    return RedisServerTarget.ofEndpointsAndLogicalName(new ArrayList<>(addresses), logicalName);
  }

  private ConfigServerTargetsSince317() {}
}
