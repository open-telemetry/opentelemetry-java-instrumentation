/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool2.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolMXBean;
import org.apache.commons.pool2.impl.GenericObjectPoolMXBean;

final class ConnectionPoolMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-commons-pool2-2.0";

  // a weak map does not make sense here because each Meter holds a reference to the pool
  // use identity comparison because pools are mutable lifecycle objects
  private static final Map<IdentityPoolKey, BatchCallback> poolMetrics = new ConcurrentHashMap<>();

  static void registerMetrics(OpenTelemetry openTelemetry, Object pool, String poolName) {
    if (pool instanceof GenericObjectPoolMXBean) {
      GenericObjectPoolMXBean objectPool = (GenericObjectPoolMXBean) pool;
      registerMetrics(
          openTelemetry,
          objectPool,
          poolName,
          objectPool::getNumActive,
          objectPool::getNumIdle,
          objectPool::getMinIdle,
          objectPool::getMaxIdle,
          objectPool::getMaxTotal,
          objectPool::getNumWaiters);
    } else if (pool instanceof GenericKeyedObjectPoolMXBean) {
      GenericKeyedObjectPoolMXBean<?> keyedPool = (GenericKeyedObjectPoolMXBean<?>) pool;
      registerMetrics(
          openTelemetry,
          keyedPool,
          poolName,
          keyedPool::getNumActive,
          keyedPool::getNumIdle,
          keyedPool::getMinIdlePerKey,
          keyedPool::getMaxIdlePerKey,
          keyedPool::getMaxTotal,
          keyedPool::getNumWaiters);
    }
  }

  private static void registerMetrics(
      OpenTelemetry openTelemetry,
      Object pool,
      String poolName,
      IntSupplier active,
      IntSupplier idle,
      IntSupplier minIdle,
      IntSupplier maxIdle,
      IntSupplier maxTotal,
      IntSupplier waiters) {
    poolMetrics.computeIfAbsent(
        new IdentityPoolKey(pool),
        unused ->
            createCallback(
                openTelemetry, poolName, active, idle, minIdle, maxIdle, maxTotal, waiters));
  }

  private static BatchCallback createCallback(
      OpenTelemetry openTelemetry,
      String poolName,
      IntSupplier active,
      IntSupplier idle,
      IntSupplier minIdle,
      IntSupplier maxIdle,
      IntSupplier maxTotal,
      IntSupplier waiters) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, poolName);

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement minIdleConnections = metrics.minIdleConnections();
    ObservableLongMeasurement maxIdleConnections = metrics.maxIdleConnections();
    ObservableLongMeasurement maxConnections = metrics.maxConnections();
    ObservableLongMeasurement pendingRequests = metrics.pendingRequestsForConnection();

    Attributes attributes = metrics.getAttributes();
    Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

    return metrics.batchCallback(
        () -> {
          connections.record(active.getAsInt(), usedConnectionsAttributes);
          connections.record(idle.getAsInt(), idleConnectionsAttributes);
          minIdleConnections.record(minIdle.getAsInt(), attributes);
          maxIdleConnections.record(maxIdle.getAsInt(), attributes);
          maxConnections.record(maxTotal.getAsInt(), attributes);
          pendingRequests.record(waiters.getAsInt(), attributes);
        },
        connections,
        minIdleConnections,
        maxIdleConnections,
        maxConnections,
        pendingRequests);
  }

  static void unregisterMetrics(Object pool) {
    BatchCallback callback = poolMetrics.remove(new IdentityPoolKey(pool));
    if (callback != null) {
      callback.close();
    }
  }

  private static final class IdentityPoolKey {
    private final Object pool;

    private IdentityPoolKey(Object pool) {
      this.pool = pool;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(@Nullable Object other) {
      return other instanceof IdentityPoolKey && pool == ((IdentityPoolKey) other).pool;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(pool);
    }
  }

  private ConnectionPoolMetrics() {}
}
