/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp.v3_0;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.instrumentation.api.metrics.internal.DurationHistogram;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryMetricsTracker implements IMetricsTracker {

  private final IMetricsTracker userMetricsTracker;

  private final BatchCallback callback;
  private final LongCounter timeouts;
  private final DurationHistogram createTime;
  private final DurationHistogram waitTime;
  private final DurationHistogram useTime;
  private final Attributes attributes;

  OpenTelemetryMetricsTracker(
      IMetricsTracker userMetricsTracker,
      BatchCallback callback,
      LongCounter timeouts,
      DurationHistogram createTime,
      DurationHistogram waitTime,
      DurationHistogram useTime,
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
    createTime.record(connectionCreatedMillis, TimeUnit.MILLISECONDS, attributes);
    userMetricsTracker.recordConnectionCreatedMillis(connectionCreatedMillis);
  }

  @Override
  public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
    waitTime.record(elapsedAcquiredNanos, TimeUnit.NANOSECONDS, attributes);
    userMetricsTracker.recordConnectionAcquiredNanos(elapsedAcquiredNanos);
  }

  @Override
  public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
    useTime.record(elapsedBorrowedMillis, TimeUnit.MILLISECONDS, attributes);
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
