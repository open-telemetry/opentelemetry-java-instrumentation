/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadruid.v1_0;

import com.alibaba.druid.pool.DruidDataSourceMBean;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class ConnectionPoolMetrics {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.alibaba-druid-1.0";

  private static final Map<DruidDataSourceMBean, DataSourceMetricsRegistration> dataSourceMetrics =
      new ConcurrentHashMap<>();
  private static final Set<String> activeDataSourceNames = ConcurrentHashMap.newKeySet();

  static void registerMetrics(
      OpenTelemetry openTelemetry, DruidDataSourceMBean dataSource, String dataSourceName) {
    dataSourceMetrics.computeIfAbsent(
        dataSource,
        unused -> {
          String rewrittenDataSourceName = reserveDataSourceName(dataSourceName);
          return new DataSourceMetricsRegistration(
              createCallback(openTelemetry, dataSource, rewrittenDataSourceName),
              rewrittenDataSourceName);
        });
  }

  private static String reserveDataSourceName(String dataSourceName) {
    if (activeDataSourceNames.add(dataSourceName)) {
      return dataSourceName;
    }
    for (int count = 2; ; count++) {
      String candidate = dataSourceName + "-" + count;
      if (activeDataSourceNames.add(candidate)) {
        return candidate;
      }
    }
  }

  private static BatchCallback createCallback(
      OpenTelemetry openTelemetry, DruidDataSourceMBean dataSource, String dataSourceName) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, dataSourceName);

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
          connections.record(dataSource.getActiveCount(), usedConnectionsAttributes);
          connections.record(dataSource.getPoolingCount(), idleConnectionsAttributes);
          pendingRequestsForConnection.record(dataSource.getWaitThreadCount(), attributes);
          minIdleConnections.record(dataSource.getMinIdle(), attributes);
          maxIdleConnections.record(dataSource.getMaxIdle(), attributes);
          maxConnections.record(dataSource.getMaxActive(), attributes);
        },
        connections,
        pendingRequestsForConnection,
        minIdleConnections,
        maxIdleConnections,
        maxConnections);
  }

  static void unregisterMetrics(DruidDataSourceMBean dataSource) {
    DataSourceMetricsRegistration registration = dataSourceMetrics.remove(dataSource);
    if (registration != null) {
      registration.close();
    }
  }

  private static final class DataSourceMetricsRegistration {
    private final BatchCallback callback;
    private final String dataSourceName;

    private DataSourceMetricsRegistration(BatchCallback callback, String dataSourceName) {
      this.callback = callback;
      this.dataSourceName = dataSourceName;
    }

    private void close() {
      activeDataSourceNames.remove(dataSourceName);
      callback.close();
    }
  }

  private ConnectionPoolMetrics() {}
}
