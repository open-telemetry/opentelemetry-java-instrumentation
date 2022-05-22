/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp;

import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.opentelemetry.api.OpenTelemetry;
import javax.annotation.Nullable;

/** Entrypoint for instrumenting Hikari database connection pools. */
public final class HikariTelemetry {

  /** Returns a new {@link HikariTelemetry} configured with the given {@link OpenTelemetry}. */
  public static HikariTelemetry create(OpenTelemetry openTelemetry) {
    return new HikariTelemetry(openTelemetry);
  }

  private final OpenTelemetry openTelemetry;

  private HikariTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Returns a new {@link MetricsTrackerFactory} that can be registered using {@link
   * com.zaxxer.hikari.HikariConfig#setMetricsTrackerFactory(MetricsTrackerFactory)}.
   */
  public MetricsTrackerFactory createMetricsTrackerFactory() {
    return createMetricsTrackerFactory(null);
  }

  /**
   * Returns a new {@link MetricsTrackerFactory} that can be registered using {@link
   * com.zaxxer.hikari.HikariConfig#setMetricsTrackerFactory(MetricsTrackerFactory)}. The {@link
   * com.zaxxer.hikari.metrics.IMetricsTracker} objects created by the returned factory will
   * delegate to trackers created by the {@code delegate} metrics tracker factory, if it is not
   * null.
   */
  public MetricsTrackerFactory createMetricsTrackerFactory(
      @Nullable MetricsTrackerFactory delegate) {
    return new OpenTelemetryMetricsTrackerFactory(openTelemetry, delegate);
  }
}
