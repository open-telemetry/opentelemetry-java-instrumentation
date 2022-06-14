/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.c3p0;

import com.mchange.v2.c3p0.impl.AbstractPoolBackedDataSource;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.api.metrics.db.DbConnectionPoolMetrics;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import javax.annotation.Nullable;

final class ConnectionPoolMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.c3p0-0.9";

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // AbstractPoolBackedDataSource implements equals() & hashCode() in IdentityTokenResolvable,
  // that's why we wrap it with IdentityDataSourceKey that uses identity comparison instead
  private static final Map<IdentityDataSourceKey, List<ObservableLongUpDownCounter>>
      dataSourceMetrics = new ConcurrentHashMap<>();

  public static void registerMetrics(
      OpenTelemetry openTelemetry, AbstractPoolBackedDataSource dataSource) {
    dataSourceMetrics.compute(
        new IdentityDataSourceKey(dataSource),
        (key, existingCounters) ->
            ConnectionPoolMetrics.createMeters(openTelemetry, key, existingCounters));
  }

  private static List<ObservableLongUpDownCounter> createMeters(
      OpenTelemetry openTelemetry,
      IdentityDataSourceKey key,
      List<ObservableLongUpDownCounter> existingCounters) {
    // remove old counters from the registry in case they were already there
    removeMetersFromRegistry(existingCounters);

    AbstractPoolBackedDataSource dataSource = key.dataSource;

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            openTelemetry, INSTRUMENTATION_NAME, dataSource.getDataSourceName());

    return Arrays.asList(
        metrics.usedConnections(wrapThrowingSupplier(dataSource::getNumBusyConnectionsDefaultUser)),
        metrics.idleConnections(wrapThrowingSupplier(dataSource::getNumIdleConnectionsDefaultUser)),
        metrics.pendingRequestsForConnection(
            wrapThrowingSupplier(dataSource::getNumThreadsAwaitingCheckoutDefaultUser)));
  }

  public static void unregisterMetrics(AbstractPoolBackedDataSource dataSource) {
    List<ObservableLongUpDownCounter> meters =
        dataSourceMetrics.remove(new IdentityDataSourceKey(dataSource));
    removeMetersFromRegistry(meters);
  }

  private static void removeMetersFromRegistry(
      @Nullable List<ObservableLongUpDownCounter> observableInstruments) {
    if (observableInstruments != null) {
      for (ObservableLongUpDownCounter observable : observableInstruments) {
        observable.close();
      }
    }
  }

  /**
   * A wrapper over {@link AbstractPoolBackedDataSource} that implements identity comparison in its
   * {@link #equals(Object)} and {@link #hashCode()} methods.
   */
  static final class IdentityDataSourceKey {
    final AbstractPoolBackedDataSource dataSource;

    IdentityDataSourceKey(AbstractPoolBackedDataSource dataSource) {
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

  static LongSupplier wrapThrowingSupplier(DataSourceIntSupplier supplier) {
    return () -> {
      try {
        return supplier.getAsInt();
      } catch (SQLException e) {
        return 0;
      }
    };
  }

  @FunctionalInterface
  interface DataSourceIntSupplier {
    int getAsInt() throws SQLException;
  }

  private ConnectionPoolMetrics() {}
}
