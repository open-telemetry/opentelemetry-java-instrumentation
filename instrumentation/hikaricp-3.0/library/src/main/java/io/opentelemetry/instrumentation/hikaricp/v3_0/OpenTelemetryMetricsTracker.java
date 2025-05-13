/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryMetricsTracker implements IMetricsTracker {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);
  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);
  private static final double MILLIS_PER_S = TimeUnit.SECONDS.toMillis(1);

  private final IMetricsTracker userMetricsTracker;

  private final BatchCallback callback;
  private final LongCounter timeouts;
  private final DoubleHistogram createTime;
  private final DoubleHistogram waitTime;
  private final DoubleHistogram useTime;
  private final Attributes attributes;

  OpenTelemetryMetricsTracker(
      IMetricsTracker userMetricsTracker,
      BatchCallback callback,
      LongCounter timeouts,
      DoubleHistogram createTime,
      DoubleHistogram waitTime,
      DoubleHistogram useTime,
      Attributes attributes) {
    this.userMetricsTracker = userMetricsTracker;
    this.callback = callback;
    this.timeouts = timeouts;
    this.createTime = createTime;
    this.waitTime = waitTime;
    this.useTime = useTime;
    this.attributes = attributes;
  }

  @Override
  public void recordConnectionCreatedMillis(long connectionCreatedMillis) {
    double time =
        emitStableDatabaseSemconv()
            ? connectionCreatedMillis / MILLIS_PER_S
            : connectionCreatedMillis;
    createTime.record(time, attributes);
    userMetricsTracker.recordConnectionCreatedMillis(connectionCreatedMillis);
  }

  @Override
  public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
    double time = elapsedAcquiredNanos / (emitStableDatabaseSemconv() ? NANOS_PER_S : NANOS_PER_MS);
    waitTime.record(time, attributes);
    userMetricsTracker.recordConnectionAcquiredNanos(elapsedAcquiredNanos);
  }

  @Override
  public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
    double time =
        emitStableDatabaseSemconv() ? elapsedBorrowedMillis / MILLIS_PER_S : elapsedBorrowedMillis;
    useTime.record(time, attributes);
    userMetricsTracker.recordConnectionUsageMillis(elapsedBorrowedMillis);
  }

  @Override
  public void recordConnectionTimeout() {
    timeouts.add(1, attributes);
    userMetricsTracker.recordConnectionTimeout();
  }

  @Override
  public void close() {
    callback.close();
    userMetricsTracker.close();
  }
}
