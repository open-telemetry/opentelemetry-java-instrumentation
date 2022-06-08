/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oracleucp;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.api.metrics.db.DbConnectionPoolMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import oracle.ucp.UniversalConnectionPool;

final class ConnectionPoolMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.orcale-ucp-11.2";

  // a weak map does not make sense here because each Meter holds a reference to the connection pool
  // none of the UniversalConnectionPool implementations contain equals()/hashCode(), so it's safe
  // to keep them in a plain ConcurrentHashMap
  private static final Map<UniversalConnectionPool, List<ObservableLongUpDownCounter>>
      dataSourceMetrics = new ConcurrentHashMap<>();

  public static void registerMetrics(
      OpenTelemetry openTelemetry, UniversalConnectionPool connectionPool) {
    dataSourceMetrics.computeIfAbsent(
        connectionPool, (unused) -> createMeters(openTelemetry, connectionPool));
  }

  private static List<ObservableLongUpDownCounter> createMeters(
      OpenTelemetry openTelemetry, UniversalConnectionPool connectionPool) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            openTelemetry, INSTRUMENTATION_NAME, connectionPool.getName());

    return Arrays.asList(
        metrics.usedConnections(connectionPool::getBorrowedConnectionsCount),
        metrics.idleConnections(connectionPool::getAvailableConnectionsCount),
        metrics.maxConnections(() -> connectionPool.getStatistics().getPeakConnectionsCount()),
        metrics.pendingRequestsForConnection(
            () -> connectionPool.getStatistics().getPendingRequestsCount()));
  }

  public static void unregisterMetrics(UniversalConnectionPool connectionPool) {
    List<ObservableLongUpDownCounter> observableInstruments =
        dataSourceMetrics.remove(connectionPool);
    if (observableInstruments != null) {
      for (ObservableLongUpDownCounter observable : observableInstruments) {
        observable.close();
      }
    }
  }

  private ConnectionPoolMetrics() {}
}
