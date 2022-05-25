/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.viburdbcp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.api.metrics.db.DbConnectionPoolMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.vibur.dbcp.ViburDBCPDataSource;

final class ConnectionPoolMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.viburdbcp-11.0";

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // ViburDBCPDataSource does not implement equals()/hashCode(), so it's safe to keep them in a
  // plain ConcurrentHashMap
  private static final Map<ViburDBCPDataSource, List<ObservableLongUpDownCounter>>
      dataSourceMetrics = new ConcurrentHashMap<>();

  public static void registerMetrics(OpenTelemetry openTelemetry, ViburDBCPDataSource dataSource) {
    dataSourceMetrics.computeIfAbsent(
        dataSource, (unused) -> createMeters(openTelemetry, dataSource));
  }

  private static List<ObservableLongUpDownCounter> createMeters(
      OpenTelemetry openTelemetry, ViburDBCPDataSource dataSource) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, dataSource.getName());

    return Arrays.asList(
        metrics.usedConnections(() -> dataSource.getPool().taken()),
        metrics.idleConnections(() -> dataSource.getPool().remainingCreated()),
        metrics.maxConnections(dataSource::getPoolMaxSize));
  }

  public static void unregisterMetrics(ViburDBCPDataSource dataSource) {
    List<ObservableLongUpDownCounter> observableInstruments = dataSourceMetrics.remove(dataSource);
    if (observableInstruments != null) {
      for (ObservableLongUpDownCounter observable : observableInstruments) {
        observable.close();
      }
    }
  }

  private ConnectionPoolMetrics() {}
}
