/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.redisson.config;

import static java.util.logging.Level.FINE;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public class ConfigServerTargetsBefore317 {

  private static final Logger logger =
      Logger.getLogger(ConfigServerTargetsBefore317.class.getName());

  @Nullable
  private static final Method CONFIG_GET_ELASTICACHE_SERVERS =
      findConfigMethod("getElasticacheServersConfig");

  @Nullable
  private static final Method CONFIG_GET_REPLICATED_SERVERS =
      findConfigMethod("getReplicatedServersConfig");

  @Nullable
  private static Method findConfigMethod(String methodName) {
    try {
      return Config.class.getDeclaredMethod(methodName);
    } catch (NoSuchMethodException ignored) {
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
      return RedisServerTarget.ofEndpoint(addressString(getAddress(singleServerConfig)));
    }
    SentinelServersConfig sentinelConfig = config.getSentinelServersConfig();
    if (sentinelConfig != null) {
      return ofAddresses(sentinelConfig.getSentinelAddresses(), sentinelConfig.getMasterName());
    }
    ClusterServersConfig clusterConfig = config.getClusterServersConfig();
    if (clusterConfig != null) {
      return ofAddresses(clusterConfig.getNodeAddresses());
    }
    RedisServerTarget elasticacheTarget =
        ofOptionalServerConfig(config, CONFIG_GET_ELASTICACHE_SERVERS);
    if (elasticacheTarget != null) {
      return elasticacheTarget;
    }
    RedisServerTarget replicatedTarget =
        ofOptionalServerConfig(config, CONFIG_GET_REPLICATED_SERVERS);
    if (replicatedTarget != null) {
      return replicatedTarget;
    }
    MasterSlaveServersConfig masterSlaveConfig = config.getMasterSlaveServersConfig();
    if (masterSlaveConfig != null) {
      return ofAddresses(
          getMasterAddress(masterSlaveConfig), masterSlaveConfig.getSlaveAddresses());
    }
    return null;
  }

  // Redisson changes the single server address return type across supported versions.
  @Nullable
  private static Object getAddress(SingleServerConfig config) {
    try {
      return config.getClass().getMethod("getAddress").invoke(config);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured Redisson single-server address", e);
      return null;
    }
  }

  @Nullable
  private static RedisServerTarget ofOptionalServerConfig(
      Config config, @Nullable Method getServerConfig) {
    if (getServerConfig == null) {
      return null;
    }
    try {
      Object serverConfig = getServerConfig.invoke(config);
      if (serverConfig == null) {
        return null;
      }
      Object addresses = serverConfig.getClass().getMethod("getNodeAddresses").invoke(serverConfig);
      return addresses instanceof Collection ? ofAddresses((Collection<?>) addresses) : null;
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured Redisson servers", e);
      return null;
    }
  }

  // Redisson changes the master address return type across supported versions.
  @Nullable
  private static Object getMasterAddress(MasterSlaveServersConfig config) {
    try {
      return config.getClass().getMethod("getMasterAddress").invoke(config);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured Redisson master address", e);
      return null;
    }
  }

  // Redisson stores addresses as URI, URL, or String across supported versions.
  @Nullable
  private static RedisServerTarget ofAddresses(@Nullable Collection<?> addresses) {
    if (addresses == null || addresses.isEmpty()) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(addresses.size());
    for (Object address : addresses) {
      endpoints.add(addressString(address));
    }
    return RedisServerTarget.ofUnorderedEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofAddresses(
      @Nullable Object firstAddress, @Nullable Collection<?> otherAddresses) {
    List<String> endpoints = new ArrayList<>();
    String firstEndpoint = addressString(firstAddress);
    if (firstEndpoint == null) {
      return null;
    }
    endpoints.add(firstEndpoint);
    List<String> sortedAddresses = new ArrayList<>();
    if (otherAddresses != null) {
      for (Object address : otherAddresses) {
        RedisServerTarget target = RedisServerTarget.ofEndpoint(addressString(address));
        if (target == null) {
          return null;
        }
        Integer port = target.getPort();
        sortedAddresses.add(
            RedisServerTarget.endpoint(target.getAddress(), port == null ? -1 : port.intValue()));
      }
    }
    Collections.sort(sortedAddresses);
    endpoints.addAll(sortedAddresses);
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofAddresses(
      @Nullable Collection<?> addresses, @Nullable String logicalName) {
    if (addresses == null || addresses.isEmpty()) {
      return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(null, logicalName);
    }
    List<String> endpoints = new ArrayList<>(addresses.size());
    for (Object address : addresses) {
      endpoints.add(addressString(address));
    }
    return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(endpoints, logicalName);
  }

  @Nullable
  private static String addressString(@Nullable Object address) {
    String value;
    if (address instanceof String) {
      value = (String) address;
    } else if (address instanceof URI) {
      value = address.toString();
    } else if (address instanceof URL) {
      value = ((URL) address).toExternalForm();
    } else {
      return null;
    }
    return value.startsWith("//") ? "redis:" + value : value;
  }

  private ConfigServerTargetsBefore317() {}
}
