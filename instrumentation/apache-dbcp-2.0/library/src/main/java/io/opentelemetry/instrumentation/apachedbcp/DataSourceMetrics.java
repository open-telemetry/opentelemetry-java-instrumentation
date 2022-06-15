/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedbcp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.api.metrics.db.DbConnectionPoolMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.dbcp2.BasicDataSourceMXBean;

final class DataSourceMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dbcp-2.0";

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // all instrumented/known implementations of BasicDataSourceMXBean do not implement
  // equals()/hashCode(), so it's safe to keep them in a plain ConcurrentHashMap
  private static final Map<BasicDataSourceMXBean, List<ObservableLongUpDownCounter>>
      dataSourceMetrics = new ConcurrentHashMap<>();

  public static void registerMetrics(
      OpenTelemetry openTelemetry, BasicDataSourceMXBean dataSource, String dataSourceName) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, dataSourceName);

    List<ObservableLongUpDownCounter> meters =
        Arrays.asList(
            metrics.usedConnections(dataSource::getNumActive),
            metrics.idleConnections(dataSource::getNumIdle),
            metrics.minIdleConnections(dataSource::getMinIdle),
            metrics.maxIdleConnections(dataSource::getMaxIdle),
            metrics.maxConnections(dataSource::getMaxTotal));

    dataSourceMetrics.put(dataSource, meters);
  }

  public static void unregisterMetrics(BasicDataSourceMXBean dataSource) {
    List<ObservableLongUpDownCounter> observableInstruments = dataSourceMetrics.remove(dataSource);
    if (observableInstruments != null) {
      for (ObservableLongUpDownCounter observable : observableInstruments) {
        observable.close();
      }
    }
  }

  private DataSourceMetrics() {}
}
