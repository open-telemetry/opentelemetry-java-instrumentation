/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.hikaricp;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import javax.annotation.Nullable;

public final class OpenTelemetryMetricsTrackerFactory implements MetricsTrackerFactory {

  @Nullable private final MetricsTrackerFactory userMetricsFactory;

  public OpenTelemetryMetricsTrackerFactory(@Nullable MetricsTrackerFactory userMetricsFactory) {
    this.userMetricsFactory = userMetricsFactory;
  }

  @Override
  public IMetricsTracker create(String poolName, PoolStats poolStats) {
    IMetricsTracker userMetrics =
        userMetricsFactory == null ? null : userMetricsFactory.create(poolName, poolStats);
    return new OpenTelemetryMetricsTracker(userMetrics, poolName, poolStats);
  }
}
