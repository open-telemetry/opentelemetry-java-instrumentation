/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryMetricsTracker implements IMetricsTracker {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private final IMetricsTracker userMetricsTracker;

  private final List<ObservableLongUpDownCounter> observableInstruments;
  private final LongCounter timeouts;
  private final DoubleHistogram createTime;
  private final DoubleHistogram waitTime;
  private final DoubleHistogram useTime;
  private final Attributes attributes;

  OpenTelemetryMetricsTracker(
      IMetricsTracker userMetricsTracker,
      List<ObservableLongUpDownCounter> observableInstruments,
      LongCounter timeouts,
      DoubleHistogram createTime,
      DoubleHistogram waitTime,
      DoubleHistogram useTime,
      Attributes attributes) {
    this.userMetricsTracker = userMetricsTracker;
    this.observableInstruments = observableInstruments;
    this.timeouts = timeouts;
    this.createTime = createTime;
    this.waitTime = waitTime;
    this.useTime = useTime;
    this.attributes = attributes;
  }

  @Override
  public void recordConnectionCreatedMillis(long connectionCreatedMillis) {
    createTime.record((double) connectionCreatedMillis, attributes);
    userMetricsTracker.recordConnectionCreatedMillis(connectionCreatedMillis);
  }

  @Override
  public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
    double millis = elapsedAcquiredNanos / NANOS_PER_MS;
    waitTime.record(millis, attributes);
    userMetricsTracker.recordConnectionAcquiredNanos(elapsedAcquiredNanos);
  }

  @Override
  public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
    useTime.record((double) elapsedBorrowedMillis, attributes);
    userMetricsTracker.recordConnectionUsageMillis(elapsedBorrowedMillis);
  }

  @Override
  public void recordConnectionTimeout() {
    timeouts.add(1, attributes);
    userMetricsTracker.recordConnectionTimeout();
  }

  @Override
  public void close() {
    for (ObservableLongUpDownCounter observable : observableInstruments) {
      observable.close();
    }
    userMetricsTracker.close();
  }
}
