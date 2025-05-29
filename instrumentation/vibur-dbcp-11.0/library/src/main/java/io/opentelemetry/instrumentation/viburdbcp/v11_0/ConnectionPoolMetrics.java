/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.viburdbcp.v11_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.vibur.dbcp.ViburDBCPDataSource;

final class ConnectionPoolMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vibur-dbcp-11.0";

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // ViburDBCPDataSource does not implement equals()/hashCode(), so it's safe to keep them in a
  // plain ConcurrentHashMap
  private static final Map<ViburDBCPDataSource, BatchCallback> dataSourceMetrics =
      new ConcurrentHashMap<>();

  public static void registerMetrics(OpenTelemetry openTelemetry, ViburDBCPDataSource dataSource) {
    dataSourceMetrics.computeIfAbsent(
        dataSource, (unused) -> createMeters(openTelemetry, dataSource));
  }

  private static BatchCallback createMeters(
      OpenTelemetry openTelemetry, ViburDBCPDataSource dataSource) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, dataSource.getName());

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement maxConnections = metrics.maxConnections();

    Attributes attributes = metrics.getAttributes();
    Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

    return metrics.batchCallback(
        () -> {
          connections.record(dataSource.getPool().taken(), usedConnectionsAttributes);
          connections.record(dataSource.getPool().remainingCreated(), idleConnectionsAttributes);
          maxConnections.record(dataSource.getPoolMaxSize(), attributes);
        },
        connections,
        maxConnections);
  }

  public static void unregisterMetrics(ViburDBCPDataSource dataSource) {
    BatchCallback callback = dataSourceMetrics.remove(dataSource);
    if (callback != null) {
      callback.close();
    }
  }

  private ConnectionPoolMetrics() {}
}
