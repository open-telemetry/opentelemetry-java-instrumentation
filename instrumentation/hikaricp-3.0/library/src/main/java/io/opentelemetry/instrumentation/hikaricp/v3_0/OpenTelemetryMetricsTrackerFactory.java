/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp.v3_0;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import javax.annotation.Nullable;

final class OpenTelemetryMetricsTrackerFactory implements MetricsTrackerFactory {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hikaricp-3.0";

  private final OpenTelemetry openTelemetry;
  @Nullable private final MetricsTrackerFactory userMetricsFactory;

  OpenTelemetryMetricsTrackerFactory(
      OpenTelemetry openTelemetry, @Nullable MetricsTrackerFactory userMetricsFactory) {
    this.openTelemetry = openTelemetry;
    this.userMetricsFactory = userMetricsFactory;
  }

  @Override
  public IMetricsTracker create(String poolName, PoolStats poolStats) {
    IMetricsTracker userMetricsTracker =
        userMetricsFactory == null
            ? NoopMetricsTracker.INSTANCE
            : userMetricsFactory.create(poolName, poolStats);

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, INSTRUMENTATION_NAME, poolName);

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement minIdleConnections = metrics.minIdleConnections();
    ObservableLongMeasurement maxConnections = metrics.maxConnections();
    ObservableLongMeasurement pendingRequestsForConnection = metrics.pendingRequestsForConnection();

    Attributes attributes = metrics.getAttributes();
    Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

    BatchCallback callback =
        metrics.batchCallback(
            () -> {
              connections.record(poolStats.getActiveConnections(), usedConnectionsAttributes);
              connections.record(poolStats.getIdleConnections(), idleConnectionsAttributes);
              minIdleConnections.record(poolStats.getMinConnections(), attributes);
              maxConnections.record(poolStats.getMaxConnections(), attributes);
              pendingRequestsForConnection.record(poolStats.getPendingThreads(), attributes);
            },
            connections,
            minIdleConnections,
            maxConnections,
            pendingRequestsForConnection);

    return new OpenTelemetryMetricsTracker(
        userMetricsTracker,
        callback,
        metrics.connectionTimeouts(),
        metrics.connectionCreateTime(),
        metrics.connectionWaitTime(),
        metrics.connectionUseTime(),
        metrics.getAttributes());
  }

  enum NoopMetricsTracker implements IMetricsTracker {
    INSTANCE
  }
}
