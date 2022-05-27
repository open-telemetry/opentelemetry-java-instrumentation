/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.api.metrics.db.DbConnectionPoolMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tomcat.jdbc.pool.DataSourceProxy;

public final class TomcatConnectionPoolMetrics {

  private static final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.tomcat-jdbc";

  private static final Map<DataSourceProxy, List<ObservableLongUpDownCounter>> dataSourceMetrics =
      new ConcurrentHashMap<>();

  public static void registerMetrics(DataSourceProxy dataSource) {
    dataSourceMetrics.computeIfAbsent(dataSource, TomcatConnectionPoolMetrics::createCounters);
  }

  private static List<ObservableLongUpDownCounter> createCounters(DataSourceProxy dataSource) {

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            openTelemetry, INSTRUMENTATION_NAME, dataSource.getPoolName());

    return Arrays.asList(
        metrics.usedConnections(dataSource::getActive),
        metrics.idleConnections(dataSource::getIdle),
        metrics.minIdleConnections(dataSource::getMinIdle),
        metrics.maxIdleConnections(dataSource::getMaxIdle),
        metrics.maxConnections(dataSource::getMaxActive),
        metrics.pendingRequestsForConnection(dataSource::getWaitCount));
  }

  public static void unregisterMetrics(DataSourceProxy dataSource) {
    List<ObservableLongUpDownCounter> counters = dataSourceMetrics.remove(dataSource);
    if (counters != null) {
      for (ObservableLongUpDownCounter meter : counters) {
        meter.close();
      }
    }
  }

  private TomcatConnectionPoolMetrics() {}
}
