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
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

// This helper is in the Redisson package to access non-public configuration methods.
public class ConfigServerTargetUtil317 {

  private static final Logger logger = Logger.getLogger(ConfigServerTargetUtil317.class.getName());

  @Nullable private static final MethodHandle SERVICE_MANAGER_GET_CFG = findServiceManagerGetCfg();
  @Nullable
  private static final MethodHandle CLUSTER_SERVERS_CONFIG_GET_DATABASE =
      findClusterServersConfigGetDatabase();

  @Nullable
  private static MethodHandle findServiceManagerGetCfg() {
    try {
      Class<?> serviceManagerClass =
          Class.forName(
              "org.redisson.connection.ServiceManager",
              false,
              ConfigServerTargetUtil317.class.getClassLoader());
      return MethodHandles.publicLookup()
          .findVirtual(serviceManagerClass, "getCfg", MethodType.methodType(Config.class));
    } catch (ReflectiveOperationException ignored) {
      // redisson only routes the configuration through a service manager between 3.20 and 3.27
      return null;
    }

    @Nullable
    private static MethodHandle findClusterServersConfigGetDatabase() {
      try {
        return MethodHandles.publicLookup()
            .findVirtual(
                ClusterServersConfig.class, "getDatabase", MethodType.methodType(int.class));
      } catch (ReflectiveOperationException ignored) {
        // Cluster database support was added in Redisson 4.7.
        return null;
      }
    }
  }

  @Nullable
  public static RedisServerTarget of(@Nullable Config config) {
    if (config == null) {
      return null;
    }

    SingleServerConfig singleServerConfig = config.getSingleServerConfig();
    if (singleServerConfig != null) {
      return RedisServerTarget.ofEndpoint(singleServerConfig.getAddress());
    }
    SentinelServersConfig sentinelConfig = config.getSentinelServersConfig();
    if (sentinelConfig != null) {
      return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(
          addressList(sentinelConfig.getSentinelAddresses()), sentinelConfig.getMasterName());
    }
    ClusterServersConfig clusterConfig = config.getClusterServersConfig();
    if (clusterConfig != null) {
      return RedisServerTarget.ofUnorderedEndpoints(addressList(clusterConfig.getNodeAddresses()));
    }
    ReplicatedServersConfig replicatedConfig = config.getReplicatedServersConfig();
    if (replicatedConfig != null) {
      return RedisServerTarget.ofUnorderedEndpoints(
          addressList(replicatedConfig.getNodeAddresses()));
    }
    MasterSlaveServersConfig masterSlaveConfig = config.getMasterSlaveServersConfig();
    if (masterSlaveConfig != null) {
      return RedisServerTarget.ofEndpointAndUnorderedEndpoints(
          masterSlaveConfig.getMasterAddress(), addressList(masterSlaveConfig.getSlaveAddresses()));
    }
    return null;
  }

  @Nullable
  public static Long databaseIndex(@Nullable Config config) {
    if (config == null) {
      return null;
    }
    SingleServerConfig singleServerConfig = config.getSingleServerConfig();
    if (singleServerConfig != null) {
      return (long) singleServerConfig.getDatabase();
    }
    SentinelServersConfig sentinelConfig = config.getSentinelServersConfig();
    if (sentinelConfig != null) {
      return (long) sentinelConfig.getDatabase();
    }
    ClusterServersConfig clusterConfig = config.getClusterServersConfig();
    if (clusterConfig != null) {
      return clusterDatabaseIndex(clusterConfig);
    }
    ReplicatedServersConfig replicatedConfig = config.getReplicatedServersConfig();
    if (replicatedConfig != null) {
      return (long) replicatedConfig.getDatabase();
    }
    MasterSlaveServersConfig masterSlaveConfig = config.getMasterSlaveServersConfig();
    return masterSlaveConfig != null ? (long) masterSlaveConfig.getDatabase() : null;
  }

  private static long clusterDatabaseIndex(ClusterServersConfig clusterConfig) {
    if (CLUSTER_SERVERS_CONFIG_GET_DATABASE == null) {
      return 0;
    }
    try {
      return (int) CLUSTER_SERVERS_CONFIG_GET_DATABASE.invoke(clusterConfig);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to read the Redisson cluster database index", t);
      return 0;
    }
  }

  @Nullable
  public static RedisServerTarget ofServiceManager(@Nullable Object serviceManager) {
    Config config = configOfServiceManager(serviceManager);
    return of(config);
  }

  @Nullable
  public static Long databaseIndexOfServiceManager(@Nullable Object serviceManager) {
    Config config = configOfServiceManager(serviceManager);
    return databaseIndex(config);
  }

  @Nullable
  private static Config configOfServiceManager(@Nullable Object serviceManager) {
    if (serviceManager == null || SERVICE_MANAGER_GET_CFG == null) {
      return null;
    }
    try {
      return (Config) SERVICE_MANAGER_GET_CFG.invoke(serviceManager);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to read the Redisson configuration from the service manager", t);
      return null;
    }
  }

  @Nullable
  private static List<String> addressList(@Nullable Collection<String> addresses) {
    return addresses == null ? null : new ArrayList<>(addresses);
  }

  private ConfigServerTargetUtil317() {}
}
