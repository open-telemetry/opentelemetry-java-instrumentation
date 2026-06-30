/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool2.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolMXBean;
import org.apache.commons.pool2.impl.GenericObjectPoolMXBean;

final class CommonsPool2Metrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-commons-pool2-2.0";

  // a weak map does not make sense here because each Meter holds a reference to the pool
  // use identity comparison because pools are mutable lifecycle objects
  private static final Map<IdentityPoolKey, BatchCallback> poolMetrics = new ConcurrentHashMap<>();
  private static final Map<String, AtomicInteger> poolNameCounters = new ConcurrentHashMap<>();

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
                openTelemetry,
                rewritePoolName(poolName),
                active,
                idle,
                minIdle,
                maxIdle,
                maxTotal,
                waiters));
  }

  private static String rewritePoolName(String poolName) {
    int count;
    while (true) {
      count =
          poolNameCounters
              .computeIfAbsent(poolName, unused -> new AtomicInteger())
              .incrementAndGet();
      if (count == 1) {
        return poolName;
      }
      poolName += count;
    }
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
    ObjectPoolMetrics metrics =
        ObjectPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, poolName);

    ObservableLongMeasurement objects = metrics.objects();
    ObservableLongMeasurement minIdleObjects = metrics.minIdleObjects();
    ObservableLongMeasurement maxIdleObjects = metrics.maxIdleObjects();
    ObservableLongMeasurement maxObjects = metrics.maxObjects();
    ObservableLongMeasurement pendingRequests = metrics.pendingRequestsForObject();

    Attributes attributes = metrics.getAttributes();

    return metrics.batchCallback(
        () -> {
          objects.record(active.getAsInt(), metrics.getUsedObjectsAttributes());
          objects.record(idle.getAsInt(), metrics.getIdleObjectsAttributes());
          minIdleObjects.record(minIdle.getAsInt(), attributes);
          maxIdleObjects.record(maxIdle.getAsInt(), attributes);
          maxObjects.record(maxTotal.getAsInt(), attributes);
          pendingRequests.record(waiters.getAsInt(), attributes);
        },
        objects,
        minIdleObjects,
        maxIdleObjects,
        maxObjects,
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

  private CommonsPool2Metrics() {}
}
