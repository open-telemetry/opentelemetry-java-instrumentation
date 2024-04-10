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
import java.util.concurrent.ConcurrentHashMap;

final class ConnectionPoolMetrics {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.alibaba-druid-1.0";

  private static final Map<DruidDataSourceMBean, BatchCallback> dataSourceMetrics =
      new ConcurrentHashMap<>();

  public static void registerMetrics(
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

    BatchCallback callback =
        metrics.batchCallback(
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

    dataSourceMetrics.put(dataSource, callback);
  }

  public static void unregisterMetrics(DruidDataSourceMBean dataSource) {
    BatchCallback callback = dataSourceMetrics.remove(dataSource);
    if (callback != null) {
      callback.close();
    }
  }

  private ConnectionPoolMetrics() {}
}
