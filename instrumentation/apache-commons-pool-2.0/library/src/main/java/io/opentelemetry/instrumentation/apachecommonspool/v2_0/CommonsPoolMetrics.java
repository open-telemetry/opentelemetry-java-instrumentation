/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolMXBean;
import org.apache.commons.pool2.impl.GenericObjectPoolMXBean;

final class CommonsPoolMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-commons-pool-2.0";

  // a weak map does not make sense here because each Meter holds a reference to the pool
  // GenericObjectPool and GenericKeyedObjectPool do not implement equals()/hashCode(), so it's
  // safe to keep them in a plain ConcurrentHashMap
  private static final Map<Object, BatchCallback> poolMetrics = new ConcurrentHashMap<>();

  static void registerMetrics(
      OpenTelemetry openTelemetry, GenericObjectPoolMXBean pool, String poolName) {
    registerMetrics(
        openTelemetry,
        pool,
        poolName,
        pool::getNumActive,
        pool::getNumIdle,
        pool::getMinIdle,
        pool::getMaxIdle,
        pool::getMaxTotal,
        pool::getNumWaiters);
  }

  static void registerMetrics(
      OpenTelemetry openTelemetry, GenericKeyedObjectPoolMXBean<?> pool, String poolName) {
    registerMetrics(
        openTelemetry,
        pool,
        poolName,
        pool::getNumActive,
        pool::getNumIdle,
        null,
        null,
        pool::getMaxTotal,
        pool::getNumWaiters);
  }

  private static void registerMetrics(
      OpenTelemetry openTelemetry,
      Object pool,
      String poolName,
      IntSupplier active,
      IntSupplier idle,
      @Nullable IntSupplier minIdle,
      @Nullable IntSupplier maxIdle,
      IntSupplier maxTotal,
      IntSupplier waiters) {
    poolMetrics.computeIfAbsent(
        pool,
        unused ->
            createCallback(
                openTelemetry, poolName, active, idle, minIdle, maxIdle, maxTotal, waiters));
  }

  private static BatchCallback createCallback(
      OpenTelemetry openTelemetry,
      String poolName,
      IntSupplier active,
      IntSupplier idle,
      @Nullable IntSupplier minIdle,
      @Nullable IntSupplier maxIdle,
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

          if (minIdle != null) {
            minIdleObjects.record(minIdle.getAsInt(), attributes);
          }
          if (maxIdle != null) {
            int maxIdleValue = maxIdle.getAsInt();
            if (maxIdleValue >= 0) {
              maxIdleObjects.record(maxIdleValue, attributes);
            }
          }

          int maxTotalValue = maxTotal.getAsInt();
          if (maxTotalValue >= 0) {
            maxObjects.record(maxTotalValue, attributes);
          }
          pendingRequests.record(waiters.getAsInt(), attributes);
        },
        objects,
        minIdleObjects,
        maxIdleObjects,
        maxObjects,
        pendingRequests);
  }

  static void unregisterMetrics(Object pool) {
    BatchCallback callback = poolMetrics.remove(pool);
    if (callback != null) {
      callback.close();
    }
  }

  private CommonsPoolMetrics() {}
}
