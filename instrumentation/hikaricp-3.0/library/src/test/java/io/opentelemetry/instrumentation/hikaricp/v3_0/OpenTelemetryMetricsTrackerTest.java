/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp.v3_0;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import org.junit.jupiter.api.Test;

class OpenTelemetryMetricsTrackerTest {

  private final IMetricsTracker userMetricsTracker = mock(IMetricsTracker.class);
  private final BatchCallback callback = mock(BatchCallback.class);
  private final LongCounter timeouts = mock(LongCounter.class);
  private final DoubleHistogram createTime = mock(DoubleHistogram.class);
  private final DoubleHistogram waitTime = mock(DoubleHistogram.class);
  private final DoubleHistogram useTime = mock(DoubleHistogram.class);
  private final Attributes attributes =
      Attributes.builder().put("db.client.connection.pool.name", "pool").build();
  private final OpenTelemetryMetricsTracker tracker =
      new OpenTelemetryMetricsTracker(
          userMetricsTracker, callback, timeouts, createTime, waitTime, useTime, attributes);

  @Test
  void recordsCreationTimeInSeconds() {
    tracker.recordConnectionCreatedMillis(1500);

    verify(createTime).record(1.5, attributes);
    verify(userMetricsTracker).recordConnectionCreatedMillis(1500);
  }

  @Test
  void recordsWaitTimeInSeconds() {
    tracker.recordConnectionAcquiredNanos(2500000000L);

    verify(waitTime).record(2.5, attributes);
    verify(userMetricsTracker).recordConnectionAcquiredNanos(2500000000L);
  }

  @Test
  void recordsUseTimeInSeconds() {
    tracker.recordConnectionUsageMillis(3500);

    verify(useTime).record(3.5, attributes);
    verify(userMetricsTracker).recordConnectionUsageMillis(3500);
  }

  @Test
  void forwardsTimeoutsAndClosesBothCallbacks() {
    tracker.recordConnectionTimeout();
    tracker.close();

    verify(timeouts).add(1, attributes);
    verify(userMetricsTracker).recordConnectionTimeout();
    verify(callback).close();
    verify(userMetricsTracker).close();
  }
}
