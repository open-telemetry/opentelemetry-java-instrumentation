/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_26;

import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3.RedissonConnectionPoolMetrics;
import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3.RedissonConnectionPoolMetrics.ConnectionPoolMetricsSource;
import org.redisson.client.RedisClient;
import org.redisson.config.MasterSlaveServersConfig;
import org.redisson.connection.ClientConnectionsEntry;
import org.redisson.connection.ConnectionsHolder;

public class RedissonSingletons {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-metrics-3.26";

  public static void registerMetrics(
      ClientConnectionsEntry entry,
      RedisClient redisClient,
      int regularMinIdle,
      int regularMax,
      MasterSlaveServersConfig config) {
    ConnectionPoolMetricsSource regular =
        createMetricsSource(entry.getConnectionsHolder(), regularMinIdle, regularMax, "regular");
    ConnectionPoolMetricsSource subscription =
        createMetricsSource(
            entry.getPubSubConnectionsHolder(),
            config.getSubscriptionConnectionMinimumIdleSize(),
            config.getSubscriptionConnectionPoolSize(),
            "subscription");

    RedissonConnectionPoolMetrics.registerMetrics(
        INSTRUMENTATION_NAME, redisClient, regular, subscription);
  }

  private static ConnectionPoolMetricsSource createMetricsSource(
      ConnectionsHolder<?> holder, int minIdleConnections, int maxConnections, String poolKind) {
    return ConnectionPoolMetricsSource.create(
        poolKind,
        minIdleConnections,
        maxConnections,
        idleConnections -> holder.getAllConnections().size() - idleConnections,
        () -> holder.getFreeConnections().size(),
        () -> holder.getFreeConnectionsCounter().queueSize());
  }

  private RedissonSingletons() {}
}
