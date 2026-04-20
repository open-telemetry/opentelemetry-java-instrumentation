/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.dbcp2.BasicDataSourceMXBean;

final class DataSourceMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dbcp-2.0";

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // all instrumented/known implementations of BasicDataSourceMXBean do not implement
  // equals()/hashCode(), so it's safe to keep them in a plain ConcurrentHashMap
  private static final Map<BasicDataSourceMXBean, BatchCallback> dataSourceMetrics =
      new ConcurrentHashMap<>();

  static void registerMetrics(
      OpenTelemetry openTelemetry, BasicDataSourceMXBean dataSource, String dataSourceName) {
    dataSourceMetrics.computeIfAbsent(
        dataSource,
        ds -> {
          DbConnectionPoolMetrics metrics =
              DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, dataSourceName);

          ObservableLongMeasurement connections = metrics.connections();
          ObservableLongMeasurement minIdleConnections = metrics.minIdleConnections();
          ObservableLongMeasurement maxIdleConnections = metrics.maxIdleConnections();
          ObservableLongMeasurement maxConnections = metrics.maxConnections();

          Attributes attributes = metrics.getAttributes();
          Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
          Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

          return metrics.batchCallback(
              () -> {
                connections.record(ds.getNumActive(), usedConnectionsAttributes);
                connections.record(ds.getNumIdle(), idleConnectionsAttributes);
                minIdleConnections.record(ds.getMinIdle(), attributes);
                maxIdleConnections.record(ds.getMaxIdle(), attributes);
                maxConnections.record(ds.getMaxTotal(), attributes);
              },
              connections,
              minIdleConnections,
              maxIdleConnections,
              maxConnections);
        });
  }

  static void unregisterMetrics(BasicDataSourceMXBean dataSource) {
    BatchCallback callback = dataSourceMetrics.remove(dataSource);
    if (callback != null) {
      callback.close();
    }
  }

  private DataSourceMetrics() {}
}
