/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp.v3_0;

import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import javax.annotation.Nullable;

/** Entrypoint for instrumenting Hikari database connection pools. */
public final class HikariTelemetry {
  private final OpenTelemetry openTelemetry;

  /** Returns a new {@link HikariTelemetry} configured with the given {@link OpenTelemetry}. */
  public static HikariTelemetry create(OpenTelemetry openTelemetry) {
    return new HikariTelemetry(openTelemetry);
  }

  private HikariTelemetry(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /**
   * Returns a new {@link MetricsTrackerFactory} that can be registered using {@link
   * com.zaxxer.hikari.HikariConfig#setMetricsTrackerFactory(MetricsTrackerFactory)}.
   */
  public MetricsTrackerFactory createMetricsTrackerFactory() {
    return instrument(null, Attributes.empty());
  }

  /**
   * Returns a new {@link MetricsTrackerFactory} that can be registered using {@link
   * com.zaxxer.hikari.HikariConfig#setMetricsTrackerFactory(MetricsTrackerFactory)}. The {@link
   * com.zaxxer.hikari.metrics.IMetricsTracker} objects created by the returned factory will
   * delegate to trackers created by the {@code delegate} metrics tracker factory, if it is not
   * null.
   *
   * @deprecated Use {@link #instrument(MetricsTrackerFactory)} instead.
   */
  @Deprecated
  public MetricsTrackerFactory createMetricsTrackerFactory(
      @Nullable MetricsTrackerFactory delegate) {
    return instrument(delegate);
  }

  /**
   * Returns an instrumented metrics tracker factory that delegates to the supplied factory, if
   * non-null. Register it using {@link
   * com.zaxxer.hikari.HikariConfig#setMetricsTrackerFactory(MetricsTrackerFactory)}.
   */
  public MetricsTrackerFactory instrument(@Nullable MetricsTrackerFactory delegate) {
    return instrument(delegate, Attributes.empty());
  }

  /**
   * Returns a metrics tracker factory that adds the supplied configured database attributes to
   * database metrics.
   *
   * @deprecated Use {@link #instrument(MetricsTrackerFactory, Attributes)} instead.
   */
  @Deprecated
  public MetricsTrackerFactory createMetricsTrackerFactory(
      @Nullable MetricsTrackerFactory delegate, Attributes databaseAttributes) {
    return instrument(delegate, databaseAttributes);
  }

  /**
   * Returns an instrumented metrics tracker factory with the supplied database attributes,
   * delegating to the supplied factory if non-null.
   */
  public MetricsTrackerFactory instrument(
      @Nullable MetricsTrackerFactory delegate, Attributes databaseAttributes) {
    return new OpenTelemetryMetricsTrackerFactory(openTelemetry, delegate, databaseAttributes);
  }
}
