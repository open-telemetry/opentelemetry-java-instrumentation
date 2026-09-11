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
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;

// This helper is in the Redisson package to access package-private configuration state.
public class ConfigServerTargetBefore317 {

  private static final Logger logger =
      Logger.getLogger(ConfigServerTargetBefore317.class.getName());

  @Nullable
  private static final Method CONFIG_GET_ELASTICACHE_SERVERS =
      findConfigMethod("getElasticacheServersConfig");

  @Nullable
  private static final Method ELASTICACHE_SERVERS_GET_NODE_ADDRESSES =
      findReturnTypeMethod(CONFIG_GET_ELASTICACHE_SERVERS, "getNodeAddresses");

  @Nullable
  private static final Method CONFIG_GET_REPLICATED_SERVERS =
      findConfigMethod("getReplicatedServersConfig");

  @Nullable
  private static final Method REPLICATED_SERVERS_GET_NODE_ADDRESSES =
      findReturnTypeMethod(CONFIG_GET_REPLICATED_SERVERS, "getNodeAddresses");

  @Nullable
  private static final Method SINGLE_SERVER_CONFIG_GET_ADDRESS =
      findPublicMethod(SingleServerConfig.class, "getAddress");

  @Nullable
  private static final Method MASTER_SLAVE_SERVERS_CONFIG_GET_MASTER_ADDRESS =
      findPublicMethod(MasterSlaveServersConfig.class, "getMasterAddress");

  @Nullable
  private static Method findConfigMethod(String methodName) {
    try {
      return Config.class.getDeclaredMethod(methodName);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  @Nullable
  private static Method findPublicMethod(Class<?> declaringClass, String methodName) {
    try {
      return declaringClass.getMethod(methodName);
    } catch (NoSuchMethodException ignored) {
      return null;
    }
  }

  @Nullable
  private static Method findReturnTypeMethod(@Nullable Method method, String returnTypeMethodName) {
    if (method == null) {
      return null;
    }
    return findPublicMethod(method.getReturnType(), returnTypeMethodName);
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
      return RedisServerTarget.ofUnorderedEndpointsAndLogicalName(
          addressList(sentinelConfig.getSentinelAddresses()), sentinelConfig.getMasterName());
    }
    ClusterServersConfig clusterConfig = config.getClusterServersConfig();
    if (clusterConfig != null) {
      return RedisServerTarget.ofUnorderedEndpoints(addressList(clusterConfig.getNodeAddresses()));
    }
    RedisServerTarget elasticacheTarget =
        ofOptionalServerConfig(
            config, CONFIG_GET_ELASTICACHE_SERVERS, ELASTICACHE_SERVERS_GET_NODE_ADDRESSES);
    if (elasticacheTarget != null) {
      return elasticacheTarget;
    }
    RedisServerTarget replicatedTarget =
        ofOptionalServerConfig(
            config, CONFIG_GET_REPLICATED_SERVERS, REPLICATED_SERVERS_GET_NODE_ADDRESSES);
    if (replicatedTarget != null) {
      return replicatedTarget;
    }
    MasterSlaveServersConfig masterSlaveConfig = config.getMasterSlaveServersConfig();
    if (masterSlaveConfig != null) {
      return RedisServerTarget.ofEndpointAndUnorderedEndpoints(
          addressString(getMasterAddress(masterSlaveConfig)),
          addressList(masterSlaveConfig.getSlaveAddresses()));
    }
    return null;
  }

  // Redisson changes the single server address return type across supported versions.
  @Nullable
  private static Object getAddress(SingleServerConfig config) {
    if (SINGLE_SERVER_CONFIG_GET_ADDRESS == null) {
      return null;
    }
    try {
      return SINGLE_SERVER_CONFIG_GET_ADDRESS.invoke(config);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured Redisson single-server address", e);
      return null;
    }
  }

  @Nullable
  private static RedisServerTarget ofOptionalServerConfig(
      Config config, @Nullable Method getServerConfig, @Nullable Method getNodeAddresses) {
    if (getServerConfig == null || getNodeAddresses == null) {
      return null;
    }
    try {
      Object serverConfig = getServerConfig.invoke(config);
      if (serverConfig == null) {
        return null;
      }
      Object addresses = getNodeAddresses.invoke(serverConfig);
      return addresses instanceof Collection
          ? RedisServerTarget.ofUnorderedEndpoints(addressList((Collection<?>) addresses))
          : null;
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured Redisson servers", e);
      return null;
    }
  }

  // Redisson changes the master address return type across supported versions.
  @Nullable
  private static Object getMasterAddress(MasterSlaveServersConfig config) {
    if (MASTER_SLAVE_SERVERS_CONFIG_GET_MASTER_ADDRESS == null) {
      return null;
    }
    try {
      return MASTER_SLAVE_SERVERS_CONFIG_GET_MASTER_ADDRESS.invoke(config);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read the configured Redisson master address", e);
      return null;
    }
  }

  // Redisson stores addresses as URI, URL, or String across supported versions.
  @Nullable
  private static List<String> addressList(@Nullable Collection<?> addresses) {
    if (addresses == null) {
      return null;
    }
    List<String> endpoints = new ArrayList<>(addresses.size());
    for (Object address : addresses) {
      endpoints.add(addressString(address));
    }
    return endpoints;
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

  private ConfigServerTargetBefore317() {}
}
