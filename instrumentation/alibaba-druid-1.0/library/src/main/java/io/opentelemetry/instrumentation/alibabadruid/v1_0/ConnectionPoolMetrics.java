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

  static void registerMetrics(
      OpenTelemetry openTelemetry, DruidDataSourceMBean dataSource, String dataSourceName) {
    dataSourceMetrics.computeIfAbsent(
        dataSource,
        ds -> {
          DbConnectionPoolMetrics metrics =
              DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, dataSourceName);

          ObservableLongMeasurement connections = metrics.connections();
          ObservableLongMeasurement minIdleConnections = metrics.minIdleConnections();
          ObservableLongMeasurement maxIdleConnections = metrics.maxIdleConnections();
          ObservableLongMeasurement maxConnections = metrics.maxConnections();
          ObservableLongMeasurement pendingRequestsForConnection =
              metrics.pendingRequestsForConnection();

          Attributes attributes = metrics.getAttributes();
          Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
          Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

          return metrics.batchCallback(
              () -> {
                connections.record(ds.getActiveCount(), usedConnectionsAttributes);
                connections.record(ds.getPoolingCount(), idleConnectionsAttributes);
                pendingRequestsForConnection.record(ds.getWaitThreadCount(), attributes);
                minIdleConnections.record(ds.getMinIdle(), attributes);
                maxIdleConnections.record(ds.getMaxIdle(), attributes);
                maxConnections.record(ds.getMaxActive(), attributes);
              },
              connections,
              pendingRequestsForConnection,
              minIdleConnections,
              maxIdleConnections,
              maxConnections);
        });
  }

  static void unregisterMetrics(DruidDataSourceMBean dataSource) {
    BatchCallback callback = dataSourceMetrics.remove(dataSource);
    if (callback != null) {
      callback.close();
    }
  }

  private ConnectionPoolMetrics() {}
}
