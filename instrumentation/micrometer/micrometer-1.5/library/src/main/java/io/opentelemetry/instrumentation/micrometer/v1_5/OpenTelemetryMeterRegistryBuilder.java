/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.TimeUnit;

/** A builder of {@link OpenTelemetryMeterRegistry}. */
public final class OpenTelemetryMeterRegistryBuilder {

  // Visible for testing
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  private final OpenTelemetry openTelemetry;
  private Clock clock = Clock.SYSTEM;
  private TimeUnit baseTimeUnit = TimeUnit.MILLISECONDS;
  private boolean prometheusMode = false;

  OpenTelemetryMeterRegistryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets a custom {@link Clock}. Useful for testing. */
  public OpenTelemetryMeterRegistryBuilder setClock(Clock clock) {
    this.clock = clock;
    return this;
  }

  /** Sets the base time unit. */
  public OpenTelemetryMeterRegistryBuilder setBaseTimeUnit(TimeUnit baseTimeUnit) {
    this.baseTimeUnit = baseTimeUnit;
    return this;
  }

  /**
   * Enables the "Prometheus mode" - this will simulate the behavior of Micrometer's {@code
   * PrometheusMeterRegistry}. The instruments will be renamed to match Micrometer instrument
   * naming, and the base time unit will be set to seconds.
   *
   * <p>Set this to {@code true} if you are using the Prometheus metrics exporter.
   */
  public OpenTelemetryMeterRegistryBuilder setPrometheusMode(boolean prometheusMode) {
    this.prometheusMode = prometheusMode;
    return this;
  }

  /**
   * Returns a new {@link OpenTelemetryMeterRegistry} with the settings of this {@link
   * OpenTelemetryMeterRegistryBuilder}.
   */
  public MeterRegistry build() {
    // prometheus mode overrides any unit settings with SECONDS
    TimeUnit baseTimeUnit = prometheusMode ? TimeUnit.SECONDS : this.baseTimeUnit;
    NamingConvention namingConvention =
        prometheusMode ? PrometheusModeNamingConvention.INSTANCE : NamingConvention.identity;

    return new OpenTelemetryMeterRegistry(
        clock,
        baseTimeUnit,
        namingConvention,
        openTelemetry.getMeterProvider().get(INSTRUMENTATION_NAME));
  }
}
