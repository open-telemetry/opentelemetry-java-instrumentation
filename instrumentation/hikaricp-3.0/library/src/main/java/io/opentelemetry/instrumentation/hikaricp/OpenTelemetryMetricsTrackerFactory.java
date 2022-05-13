/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.api.metrics.db.DbConnectionPoolMetrics;
import java.util.Arrays;
import java.util.List;
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

    List<ObservableLongUpDownCounter> observableInstruments =
        Arrays.asList(
            metrics.usedConnections(poolStats::getActiveConnections),
            metrics.idleConnections(poolStats::getIdleConnections),
            metrics.minIdleConnections(poolStats::getMinConnections),
            metrics.maxConnections(poolStats::getMaxConnections),
            metrics.pendingRequestsForConnection(poolStats::getPendingThreads));

    return new OpenTelemetryMetricsTracker(
        userMetricsTracker,
        observableInstruments,
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
