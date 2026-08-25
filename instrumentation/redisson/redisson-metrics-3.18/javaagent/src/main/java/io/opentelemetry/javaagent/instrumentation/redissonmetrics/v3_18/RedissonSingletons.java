/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v3_18;

import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v3_18.RedissonConnectionPoolMetrics;
import io.opentelemetry.javaagent.instrumentation.redissonmetrics.common.v3_18.RedissonConnectionPoolMetrics.ConnectionPoolMetricsSource;
import java.util.Collection;
import org.redisson.client.RedisClient;
import org.redisson.misc.AsyncSemaphore;

public class RedissonSingletons {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-metrics-3.18";

  @SuppressWarnings("TooManyParameters")
  public static void registerMetrics(
      RedisClient redisClient,
      int regularMinIdle,
      int regularMax,
      AsyncSemaphore regularSemaphore,
      Collection<?> regularFreeConnections,
      int subscriptionMinIdle,
      int subscriptionMax,
      AsyncSemaphore subscriptionSemaphore,
      Collection<?> subscriptionFreeConnections) {
    ConnectionPoolMetricsSource regular =
        ConnectionPoolMetricsSource.create(
            "regular",
            regularMinIdle,
            regularMax,
            unused -> regularMax - regularSemaphore.getCounter(),
            regularFreeConnections::size,
            regularSemaphore::queueSize);
    ConnectionPoolMetricsSource subscription =
        ConnectionPoolMetricsSource.create(
            "subscription",
            subscriptionMinIdle,
            subscriptionMax,
            unused -> subscriptionMax - subscriptionSemaphore.getCounter(),
            subscriptionFreeConnections::size,
            subscriptionSemaphore::queueSize);
    RedissonConnectionPoolMetrics.registerMetrics(
        INSTRUMENTATION_NAME, redisClient, regular, subscription);
  }

  private RedissonSingletons() {}
}
