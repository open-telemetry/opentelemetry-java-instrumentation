/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.redisson.config;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

// This helper is in the Redisson package in order to read the per mode configuration, which Config
// only exposes to its own package.
public final class ConfigServerTargetsBefore317 {

  /**
   * The target the configuration names, which is the Sentinel endpoints scoped by their master, or
   * the cluster nodes when the client was configured against a cluster.
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
    return null;
  }

  /**
   * Renders addresses that redisson holds as {@code java.net.URI} before 3.16 and as {@code String}
   * from 3.16 on, which is why they are read as plain objects.
   */
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
