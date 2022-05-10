/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.PoolStats;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.api.metrics.db.DbConnectionPoolMetrics;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

final class OpenTelemetryMetricsTracker implements IMetricsTracker {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hikaricp-3.0";
  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  @Nullable private final IMetricsTracker userMetricsTracker;

  private final List<ObservableLongUpDownCounter> observableInstruments;
  private final LongCounter timeouts;
  private final DoubleHistogram createTime;
  private final DoubleHistogram waitTime;
  private final DoubleHistogram useTime;
  private final Attributes attributes;

  public OpenTelemetryMetricsTracker(
      @Nullable IMetricsTracker userMetricsTracker, String poolName, PoolStats poolStats) {

    this.userMetricsTracker = userMetricsTracker;

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, poolName);

    this.observableInstruments =
        Arrays.asList(
            metrics.usedConnections(poolStats::getActiveConnections),
            metrics.idleConnections(poolStats::getIdleConnections),
            metrics.minIdleConnections(poolStats::getMinConnections),
            metrics.maxConnections(poolStats::getMaxConnections),
            metrics.pendingRequestsForConnection(poolStats::getPendingThreads));

    this.timeouts = metrics.connectionTimeouts();
    this.createTime = metrics.connectionCreateTime();
    this.waitTime = metrics.connectionWaitTime();
    this.useTime = metrics.connectionUseTime();
    this.attributes = metrics.getAttributes();
  }

  @Override
  public void recordConnectionCreatedMillis(long connectionCreatedMillis) {
    createTime.record((double) connectionCreatedMillis, attributes);
    if (userMetricsTracker != null) {
      userMetricsTracker.recordConnectionCreatedMillis(connectionCreatedMillis);
    }
  }

  @Override
  public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
    double millis = elapsedAcquiredNanos / NANOS_PER_MS;
    waitTime.record(millis, attributes);
    if (userMetricsTracker != null) {
      userMetricsTracker.recordConnectionAcquiredNanos(elapsedAcquiredNanos);
    }
  }

  @Override
  public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
    useTime.record((double) elapsedBorrowedMillis, attributes);
    if (userMetricsTracker != null) {
      userMetricsTracker.recordConnectionUsageMillis(elapsedBorrowedMillis);
    }
  }

  @Override
  public void recordConnectionTimeout() {
    timeouts.add(1, attributes);
    if (userMetricsTracker != null) {
      userMetricsTracker.recordConnectionTimeout();
    }
  }

  @Override
  public void close() {
    for (ObservableLongUpDownCounter observable : observableInstruments) {
      observable.close();
    }
    if (userMetricsTracker != null) {
      userMetricsTracker.close();
    }
  }
}
