/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redissonmetrics.v2_3;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;

public class RedissonConnectionPoolMetrics {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-metrics-2.3";

  private static final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
  // RedisClient does not override equals/hashCode, so map keys retain object identity semantics.
  private static final Map<RedisClient, BatchCallback> regularPoolMetrics =
      new ConcurrentHashMap<>();
  private static final Map<RedisClient, BatchCallback> subscriptionPoolMetrics =
      new ConcurrentHashMap<>();

  public static void registerMetrics(
      RedisClient redisClient,
      int minIdleConnections,
      int maxConnections,
      NodeType nodeType,
      Supplier<Integer> availableConnections,
      Collection<?> idleConnections,
      @Nullable Supplier<Integer> pendingRequests) {
    registerPoolMetrics(
        regularPoolMetrics,
        "regular",
        redisClient,
        minIdleConnections,
        maxConnections,
        nodeType,
        availableConnections,
        idleConnections,
        pendingRequests);
  }

  public static void registerSubscriptionMetrics(
      RedisClient redisClient,
      Object connectionManager,
      int minIdleConnections,
      int maxConnections,
      NodeType nodeType,
      Supplier<Integer> availableConnections,
      Collection<?> idleConnections,
      @Nullable Supplier<Integer> pendingRequests) {
    if (maxConnections > 0) {
      minIdleConnections =
          RedissonConnectionPoolAccessor.getSubscriptionMinimumIdleSize(
              connectionManager, minIdleConnections);
    }
    registerPoolMetrics(
        subscriptionPoolMetrics,
        "subscription",
        redisClient,
        minIdleConnections,
        maxConnections,
        nodeType,
        availableConnections,
        idleConnections,
        pendingRequests);
  }

  private static void registerPoolMetrics(
      Map<RedisClient, BatchCallback> poolMetrics,
      String poolKind,
      RedisClient redisClient,
      int minIdleConnections,
      int maxConnections,
      NodeType nodeType,
      Supplier<Integer> availableConnections,
      Collection<?> idleConnections,
      @Nullable Supplier<Integer> pendingRequests) {
    if (maxConnections <= 0) {
      return;
    }

    poolMetrics.computeIfAbsent(
        redisClient,
        unused ->
            createCallback(
                poolName(redisClient, nodeType, poolKind),
                minIdleConnections,
                maxConnections,
                availableConnections,
                idleConnections,
                pendingRequests));
  }

  public static void unregisterMetrics(RedisClient redisClient) {
    closeCallback(regularPoolMetrics.remove(redisClient));
    closeCallback(subscriptionPoolMetrics.remove(redisClient));
  }

  private static void closeCallback(@Nullable BatchCallback callback) {
    if (callback != null) {
      callback.close();
    }
  }

  private static BatchCallback createCallback(
      String poolName,
      int minIdleConnections,
      int maxConnections,
      Supplier<Integer> availableConnections,
      Collection<?> idleConnections,
      @Nullable Supplier<Integer> pendingRequestsSupplier) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, poolName);

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement minIdle = metrics.minIdleConnections();
    ObservableLongMeasurement max = metrics.maxConnections();
    ObservableLongMeasurement pendingRequests =
        pendingRequestsSupplier == null ? null : metrics.pendingRequestsForConnection();

    Attributes attributes = metrics.getAttributes();
    Attributes usedAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleAttributes = metrics.getIdleConnectionsAttributes();

    Runnable callback =
        () -> {
          Integer availableConnectionPermits = availableConnections.get();
          if (availableConnectionPermits != null) {
            connections.record(
                Math.max(0, maxConnections - availableConnectionPermits), usedAttributes);
          }
          connections.record(idleConnections.size(), idleAttributes);
          minIdle.record(minIdleConnections, attributes);
          max.record(maxConnections, attributes);
          if (pendingRequestsSupplier != null && pendingRequests != null) {
            Integer pendingRequestCount = pendingRequestsSupplier.get();
            if (pendingRequestCount != null) {
              pendingRequests.record(pendingRequestCount, attributes);
            }
          }
        };

    if (pendingRequests == null) {
      return metrics.batchCallback(callback, connections, minIdle, max);
    }
    return metrics.batchCallback(callback, connections, minIdle, max, pendingRequests);
  }

  private static String poolName(RedisClient redisClient, NodeType nodeType, String poolKind) {
    String prefix = nodeType.name().toLowerCase(Locale.ROOT);
    InetSocketAddress address = redisClient.getAddr();
    return prefix + "-" + poolKind + "-" + address.getHostString() + ":" + address.getPort();
  }

  private RedissonConnectionPoolMetrics() {}
}
