/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3.RedissonConnectionPoolMetrics;
import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v2_3.RedissonConnectionPoolMetrics.ConnectionPoolMetricsSource;
import java.util.Collection;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.redisson.client.RedisClient;

public class RedissonSingletons {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-metrics-2.3";

  @SuppressWarnings("TooManyParameters")
  public static void registerMetrics(
      RedisClient redisClient,
      int regularMinIdle,
      int regularMax,
      Object regularCounter,
      Collection<?> regularFreeConnections,
      int subscriptionMinIdle,
      int subscriptionMax,
      Object connectionManager,
      Object subscriptionCounter,
      Collection<?> subscriptionFreeConnections) {
    if (subscriptionMax > 0) {
      subscriptionMinIdle =
          RedissonConnectionPoolAccessor.getSubscriptionMinimumIdleSize(
              connectionManager, subscriptionMinIdle);
    }

    ConnectionPoolMetricsSource regular =
        createSource("regular", regularMinIdle, regularMax, regularCounter, regularFreeConnections);
    ConnectionPoolMetricsSource subscription =
        createSource(
            "subscription",
            subscriptionMinIdle,
            subscriptionMax,
            subscriptionCounter,
            subscriptionFreeConnections);
    RedissonConnectionPoolMetrics.registerMetrics(
        INSTRUMENTATION_NAME, redisClient, regular, subscription);
  }

  @Nullable
  private static ConnectionPoolMetricsSource createSource(
      String poolKind,
      int minIdleConnections,
      int maxConnections,
      Object counter,
      Collection<?> idleConnections) {
    Supplier<Integer> availableConnections =
        RedissonConnectionPoolAccessor.availableConnectionsSupplier(counter);
    if (availableConnections == null) {
      return null;
    }

    return ConnectionPoolMetricsSource.create(
        poolKind,
        minIdleConnections,
        maxConnections,
        unused -> {
          Integer available = availableConnections.get();
          return available == null ? null : maxConnections - available;
        },
        idleConnections::size,
        RedissonConnectionPoolAccessor.pendingRequestsSupplier(counter));
  }

  private RedissonSingletons() {}
}
