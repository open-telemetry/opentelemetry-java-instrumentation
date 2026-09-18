/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oracleucp.v11_2;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import oracle.ucp.UniversalConnectionPool;

final class ConnectionPoolMetrics {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.oracle-ucp-11.2";

  // a weak map does not make sense here because each Meter holds a reference to the connection pool
  // none of the UniversalConnectionPool implementations contain equals()/hashCode(), so it's safe
  // to keep them in a plain ConcurrentHashMap
  private static final Map<UniversalConnectionPool, BatchCallback> connectionPoolMetrics =
      new ConcurrentHashMap<>();

  static void registerMetrics(OpenTelemetry openTelemetry, UniversalConnectionPool connectionPool) {
    registerMetrics(openTelemetry, connectionPool, connectionPool.getName());
  }

  static void registerMetrics(
      OpenTelemetry openTelemetry, UniversalConnectionPool connectionPool, String poolName) {
    connectionPoolMetrics.computeIfAbsent(
        connectionPool, unused -> createMeters(openTelemetry, connectionPool, poolName));
  }

  private static BatchCallback createMeters(
      OpenTelemetry openTelemetry, UniversalConnectionPool connectionPool, String poolName) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, poolName);

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement maxConnections = metrics.maxConnections();
    ObservableLongMeasurement pendingRequestsForConnection = metrics.pendingRequestsForConnection();

    Attributes attributes = metrics.getAttributes();
    Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

    return metrics.batchCallback(
        () -> {
          connections.record(
              connectionPool.getBorrowedConnectionsCount(), usedConnectionsAttributes);
          connections.record(
              connectionPool.getAvailableConnectionsCount(), idleConnectionsAttributes);
          maxConnections.record(connectionPool.getMaxPoolSize(), attributes);
          pendingRequestsForConnection.record(
              connectionPool.getStatistics().getPendingRequestsCount(), attributes);
        },
        connections,
        maxConnections,
        pendingRequestsForConnection);
  }

  static void unregisterMetrics(UniversalConnectionPool connectionPool) {
    BatchCallback callback = connectionPoolMetrics.remove(connectionPool);
    if (callback != null) {
      callback.close();
    }
  }

  private ConnectionPoolMetrics() {}
}
