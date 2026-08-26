/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.redisson.config;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

public final class ConfigServerTargetsBefore317 {

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
    MasterSlaveServersConfig masterSlaveConfig = config.getMasterSlaveServersConfig();
    if (masterSlaveConfig != null) {
      return ofAddresses(
          getMasterAddress(masterSlaveConfig), masterSlaveConfig.getSlaveAddresses());
    }
    return null;
  }

  // Redisson changes the master address return type across supported versions.
  private static Object getMasterAddress(MasterSlaveServersConfig config) {
    try {
      return config.getClass().getMethod("getMasterAddress").invoke(config);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Could not read Redisson master address", e);
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
      if (address != null) {
        endpoints.add(address.toString());
      }
    }
    Collections.sort(endpoints);
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofAddresses(
      @Nullable Object firstAddress, @Nullable Collection<?> otherAddresses) {
    List<String> endpoints = new ArrayList<>();
    if (firstAddress != null) {
      endpoints.add(firstAddress.toString());
    }
    Set<String> sortedAddresses = new TreeSet<>();
    if (otherAddresses != null) {
      for (Object address : otherAddresses) {
        if (address != null) {
          sortedAddresses.add(address.toString());
        }
      }
    }
    endpoints.addAll(sortedAddresses);
    return RedisServerTarget.ofEndpoints(endpoints);
  }

  @Nullable
  private static RedisServerTarget ofAddresses(
      @Nullable Collection<?> addresses, @Nullable String logicalName) {
    if (addresses == null || addresses.isEmpty()) {
      return RedisServerTarget.ofEndpointsAndLogicalName(null, logicalName);
    }
    List<String> endpoints = new ArrayList<>(addresses.size());
    for (Object address : addresses) {
      if (address != null) {
        endpoints.add(address.toString());
      }
    }
    return RedisServerTarget.ofEndpointsAndLogicalName(endpoints, logicalName);
  }

  private ConfigServerTargetsBefore317() {}
}
