/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import java.util.concurrent.TimeUnit;

/** A builder of {@link OpenTelemetryMeterRegistry}. */
public final class OpenTelemetryMeterRegistryBuilder {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  private final OpenTelemetry openTelemetry;
  private Clock clock = Clock.SYSTEM;
  private TimeUnit baseTimeUnit;
  private boolean prometheusMode;

  OpenTelemetryMeterRegistryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;

    Config config = Config.get();

    prometheusMode =
        config.getBoolean("otel.instrumentation.micrometer.prometheus-mode.enabled", false);

    // use seconds as the default unit if prometheus mode is enabled
    TimeUnit defaultBaseTimeUnit = prometheusMode ? TimeUnit.SECONDS : TimeUnit.MILLISECONDS;
    baseTimeUnit =
        TimeUnitHelper.parseConfigValue(
            config.getString("otel.instrumentation.micrometer.base-time-unit"),
            defaultBaseTimeUnit);
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
    return setBaseTimeUnit(TimeUnit.SECONDS);
  }

  /**
   * Returns a new {@link OpenTelemetryMeterRegistry} with the settings of this {@link
   * OpenTelemetryMeterRegistryBuilder}.
   */
  public MeterRegistry build() {
    return new OpenTelemetryMeterRegistry(
        clock,
        baseTimeUnit,
        prometheusMode,
        openTelemetry.getMeterProvider().get(INSTRUMENTATION_NAME));
  }
}
