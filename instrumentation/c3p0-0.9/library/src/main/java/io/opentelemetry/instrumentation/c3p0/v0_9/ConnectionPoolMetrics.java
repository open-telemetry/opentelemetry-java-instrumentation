/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.c3p0.v0_9;

import static java.util.logging.Level.FINE;

import com.mchange.v2.c3p0.PooledDataSource;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class ConnectionPoolMetrics {

  private static final Logger logger = Logger.getLogger(ConnectionPoolMetrics.class.getName());

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.c3p0-0.9";
  private static final String DEFAULT_POOL_NAME = "c3p0";
  private static final AtomicInteger idGenerator = new AtomicInteger(1);

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // PooledDataSource implements equals() & hashCode() in IdentityTokenResolvable,
  // that's why we wrap it with IdentityDataSourceKey that uses identity comparison instead
  private static final Map<IdentityDataSourceKey, BatchCallback> dataSourceMetrics =
      new ConcurrentHashMap<>();
  private static final Map<IdentityDataSourceKey, String> generatedPoolNames =
      new ConcurrentHashMap<>();

  static void registerMetrics(OpenTelemetry openTelemetry, PooledDataSource dataSource) {
    dataSourceMetrics.compute(
        new IdentityDataSourceKey(dataSource),
        (key, existingCallback) ->
            ConnectionPoolMetrics.createMeters(openTelemetry, key, existingCallback));
  }

  private static BatchCallback createMeters(
      OpenTelemetry openTelemetry,
      IdentityDataSourceKey key,
      @Nullable BatchCallback existingCallback) {
    // remove old counters from the registry in case they were already there
    removeMetersFromRegistry(existingCallback);

    PooledDataSource dataSource = key.dataSource;

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, getPoolName(key));

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement pendingRequestsForConnection = metrics.pendingRequestsForConnection();

    Attributes attributes = metrics.getAttributes();
    Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

    return metrics.batchCallback(
        () -> {
          try {
            connections.record(
                dataSource.getNumBusyConnectionsDefaultUser(), usedConnectionsAttributes);
            connections.record(
                dataSource.getNumIdleConnectionsDefaultUser(), idleConnectionsAttributes);
            pendingRequestsForConnection.record(
                dataSource.getNumThreadsAwaitingCheckoutDefaultUser(), attributes);
          } catch (SQLException e) {
            logger.log(FINE, "Failed to get C3P0 datasource metric", e);
          }
        },
        connections,
        pendingRequestsForConnection);
  }

  private static String getPoolName(IdentityDataSourceKey key) {
    PooledDataSource dataSource = key.dataSource;
    String dataSourceName = dataSource.getDataSourceName();
    if (dataSourceName == null || dataSourceName.equals(dataSource.getIdentityToken())) {
      return generatedPoolNames.computeIfAbsent(
          key, unused -> DEFAULT_POOL_NAME + "-" + idGenerator.getAndIncrement());
    }
    return dataSourceName;
  }

  static void unregisterMetrics(PooledDataSource dataSource) {
    IdentityDataSourceKey key = new IdentityDataSourceKey(dataSource);
    BatchCallback callback = dataSourceMetrics.remove(key);
    generatedPoolNames.remove(key);
    removeMetersFromRegistry(callback);
  }

  private static void removeMetersFromRegistry(@Nullable BatchCallback callback) {
    if (callback != null) {
      callback.close();
    }
  }

  /**
   * A wrapper over {@link PooledDataSource} that implements identity comparison in its {@link
   * #equals(Object)} and {@link #hashCode()} methods.
   */
  private static final class IdentityDataSourceKey {
    private final PooledDataSource dataSource;

    IdentityDataSourceKey(PooledDataSource dataSource) {
      this.dataSource = dataSource;
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public boolean equals(@Nullable Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      IdentityDataSourceKey that = (IdentityDataSourceKey) o;
      return dataSource == that.dataSource;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(dataSource);
    }

    @Override
    public String toString() {
      return dataSource.toString();
    }
  }

  private ConnectionPoolMetrics() {}
}
