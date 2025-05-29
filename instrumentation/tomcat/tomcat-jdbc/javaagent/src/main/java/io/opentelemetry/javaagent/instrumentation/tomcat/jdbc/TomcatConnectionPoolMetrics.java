/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tomcat.jdbc.pool.DataSourceProxy;

public final class TomcatConnectionPoolMetrics {

  private static final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.tomcat-jdbc";

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // DataSourceProxy does not implement equals()/hashCode(), so it's safe to keep them in a plain
  // ConcurrentHashMap
  private static final Map<DataSourceProxy, BatchCallback> dataSourceMetrics =
      new ConcurrentHashMap<>();

  public static void registerMetrics(DataSourceProxy dataSource) {
    dataSourceMetrics.computeIfAbsent(dataSource, TomcatConnectionPoolMetrics::createInstruments);
  }

  private static BatchCallback createInstruments(DataSourceProxy dataSource) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            openTelemetry, INSTRUMENTATION_NAME, dataSource.getPoolName());

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement minIdleConnections = metrics.minIdleConnections();
    ObservableLongMeasurement maxIdleConnections = metrics.maxIdleConnections();
    ObservableLongMeasurement maxConnections = metrics.maxConnections();
    ObservableLongMeasurement pendingRequestsForConnection = metrics.pendingRequestsForConnection();

    Attributes attributes = metrics.getAttributes();
    Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

    return metrics.batchCallback(
        () -> {
          connections.record(dataSource.getActive(), usedConnectionsAttributes);
          connections.record(dataSource.getIdle(), idleConnectionsAttributes);
          minIdleConnections.record(dataSource.getMinIdle(), attributes);
          maxIdleConnections.record(dataSource.getMaxIdle(), attributes);
          maxConnections.record(dataSource.getMaxActive(), attributes);
          pendingRequestsForConnection.record(dataSource.getWaitCount(), attributes);
        },
        connections,
        minIdleConnections,
        maxIdleConnections,
        maxConnections,
        pendingRequestsForConnection);
  }

  public static void unregisterMetrics(DataSourceProxy dataSource) {
    BatchCallback callback = dataSourceMetrics.remove(dataSource);
    if (callback != null) {
      callback.close();
    }
  }

  private TomcatConnectionPoolMetrics() {}
}
