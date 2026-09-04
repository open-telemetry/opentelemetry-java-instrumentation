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
import java.util.logging.Logger;
import javax.annotation.Nullable;

// This helper is in the Redisson package to access package-private configuration state.
public class ConfigServerTargetsSince317 {

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
    } catch (ReflectiveOperationException ignored) {
      // redisson only routes the configuration through a service manager between 3.20 and 3.27
      return null;
    }
  }

  @Nullable
  public static RedisServerTarget of(@Nullable Config config) {
    if (config == null) {
      return null;
    }
    SingleServerConfig singleServerConfig = config.getSingleServerConfig();
    if (singleServerConfig != null) {
      return RedisServerTarget.ofEndpoint(getAddress(singleServerConfig));
    }
    SentinelServersConfig sentinelConfig = config.getSentinelServersConfig();
    if (sentinelConfig != null) {
      return ofSentinelAddresses(
          sentinelConfig.getSentinelAddresses(), sentinelConfig.getMasterName());
    }
    ClusterServersConfig clusterConfig = config.getClusterServersConfig();
    if (clusterConfig != null) {
      return ofUnorderedAddresses(clusterConfig.getNodeAddresses());
    }
    ReplicatedServersConfig replicatedConfig = config.getReplicatedServersConfig();
    if (replicatedConfig != null) {
      return ofUnorderedAddresses(replicatedConfig.getNodeAddresses());
    }
    MasterSlaveServersConfig masterSlaveConfig = config.getMasterSlaveServersConfig();
    if (masterSlaveConfig != null) {
      return ofMasterSlaveAddresses(
          masterSlaveConfig.getMasterAddress(), masterSlaveConfig.getSlaveAddresses());
    }
    return null;
  }

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

  // Redisson changes the single server address return type across supported versions.
  @Nullable
  private static String getAddress(SingleServerConfig config) {
    try {
      Object address = config.getClass().getMethod("getAddress").invoke(config);
      return address != null ? address.toString() : null;
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured Redisson single-server address", e);
      return null;
    }
  }

  @Nullable
  private static RedisServerTarget ofUnorderedAddresses(@Nullable Collection<String> addresses) {
    if (addresses == null || addresses.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(addresses);
    return RedisServerTarget.ofUnorderedEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofMasterSlaveAddresses(
      @Nullable String firstAddress, @Nullable Collection<String> otherAddresses) {
    List<String> endpoints = new ArrayList<>();
    if (firstAddress != null) {
      endpoints.add(firstAddress);
    }
    List<String> sortedAddresses = new ArrayList<>();
    if (otherAddresses != null) {
      for (String address : otherAddresses) {
        RedisServerTarget target = RedisServerTarget.ofEndpoint(address);
        if (target == null) {
          return null;
        }
        Integer port = target.getPort();
        sortedAddresses.add(
            port == null
                ? target.getAddress()
                : RedisServerTarget.endpoint(target.getAddress(), port.intValue()));
      }
    }
    Collections.sort(sortedAddresses);
    endpoints.addAll(sortedAddresses);
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofSentinelAddresses(
      @Nullable Collection<String> addresses, @Nullable String logicalName) {
    if (addresses == null || addresses.isEmpty()) {
      return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(null, logicalName);
    }
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(
        new ArrayList<>(addresses), logicalName);
  }

  private ConfigServerTargetsSince317() {}
}
