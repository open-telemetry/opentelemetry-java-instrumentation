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
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import org.redisson.api.NodeType;
import org.redisson.client.RedisClient;

public final class RedissonConnectionPoolMetrics {

  static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-metrics-2.3";

  private static final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
  private static final Map<IdentityKey, BatchCallback> poolMetrics = new ConcurrentHashMap<>();

  public static void registerMetrics(
      RedisClient redisClient,
      int minIdleConnections,
      int maxConnections,
      NodeType nodeType,
      IntSupplier availableConnections,
      Collection<?> idleConnections,
      @Nullable IntSupplier pendingRequests) {
    if (maxConnections <= 0) {
      return;
    }

    poolMetrics.computeIfAbsent(
        new IdentityKey(redisClient),
        unused ->
            createCallback(
                poolName(redisClient, nodeType),
                minIdleConnections,
                maxConnections,
                availableConnections,
                idleConnections,
                pendingRequests));
  }

  public static void unregisterMetrics(RedisClient redisClient) {
    BatchCallback callback = poolMetrics.remove(new IdentityKey(redisClient));
    if (callback != null) {
      callback.close();
    }
  }

  private static BatchCallback createCallback(
      String poolName,
      int minIdleConnections,
      int maxConnections,
      IntSupplier availableConnections,
      Collection<?> idleConnections,
      @Nullable IntSupplier pendingRequestsSupplier) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, poolName);

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement minIdle = metrics.minIdleConnections();
    ObservableLongMeasurement maxIdle = metrics.maxIdleConnections();
    ObservableLongMeasurement max = metrics.maxConnections();
    ObservableLongMeasurement pendingRequests =
        pendingRequestsSupplier == null ? null : metrics.pendingRequestsForConnection();

    Attributes attributes = metrics.getAttributes();
    Attributes usedAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleAttributes = metrics.getIdleConnectionsAttributes();

    Runnable callback =
        () -> {
          int availableConnectionPermits = Math.max(0, availableConnections.getAsInt());
          int idleConnectionCount = Math.max(0, idleConnections.size());
          connections.record(
              Math.max(0, maxConnections - availableConnectionPermits), usedAttributes);
          connections.record(idleConnectionCount, idleAttributes);
          minIdle.record(minIdleConnections, attributes);
          maxIdle.record(maxConnections, attributes);
          max.record(maxConnections, attributes);
          if (pendingRequestsSupplier != null) {
            pendingRequests.record(pendingRequestsSupplier.getAsInt(), attributes);
          }
        };

    if (pendingRequests == null) {
      return metrics.batchCallback(callback, connections, minIdle, maxIdle, max);
    }
    return metrics.batchCallback(callback, connections, minIdle, maxIdle, max, pendingRequests);
  }

  private static String poolName(RedisClient redisClient, @Nullable NodeType nodeType) {
    String prefix = nodeType == null ? "unknown" : nodeType.name().toLowerCase(Locale.ROOT);
    InetSocketAddress address = redisClient.getAddr();
    return prefix + "-" + address.getHostString() + ":" + address.getPort();
  }

  private static final class IdentityKey {
    private final RedisClient redisClient;

    private IdentityKey(RedisClient redisClient) {
      this.redisClient = redisClient;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(@Nullable Object other) {
      return other instanceof IdentityKey && redisClient == ((IdentityKey) other).redisClient;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(redisClient);
    }
  }

  private RedissonConnectionPoolMetrics() {}
}
