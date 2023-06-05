/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.TimeUnit;

/** A builder of {@link OpenTelemetryMeterRegistry}. */
public final class OpenTelemetryMeterRegistryBuilder {

  // Visible for testing
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  private final OpenTelemetry openTelemetry;
  private Clock clock = Clock.SYSTEM;
  private TimeUnit baseTimeUnit = TimeUnit.SECONDS;
  private boolean prometheusMode = false;
  private boolean histogramGaugesEnabled = false;

  OpenTelemetryMeterRegistryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Sets a custom {@link Clock}. Useful for testing. */
  @CanIgnoreReturnValue
  public OpenTelemetryMeterRegistryBuilder setClock(Clock clock) {
    this.clock = clock;
    return this;
  }

  /** Sets the base time unit. */
  @CanIgnoreReturnValue
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
  @CanIgnoreReturnValue
  public OpenTelemetryMeterRegistryBuilder setPrometheusMode(boolean prometheusMode) {
    this.prometheusMode = prometheusMode;
    return this;
  }

  /**
   * Enables the generation of gauge-based Micrometer histograms. While the Micrometer bridge is
   * able to map Micrometer's {@link DistributionSummary} and {@link Timer} service level objectives
   * to OpenTelemetry histogram buckets, it might not cover all cases that are normally supported by
   * Micrometer (e.g. the bridge is not able to translate percentiles). With this setting enabled,
   * the Micrometer bridge will additionally emit Micrometer service level objectives and
   * percentiles as separate gauges.
   *
   * <p>Note that this setting does not concern the {@link LongTaskTimer}, as it is not bridged to
   * an OpenTelemetry histogram.
   *
   * <p>This is disabled by default, set this to {@code true} to enable gauge-based Micrometer
   * histograms.
   */
  @CanIgnoreReturnValue
  public OpenTelemetryMeterRegistryBuilder setMicrometerHistogramGaugesEnabled(
      boolean histogramGaugesEnabled) {
    this.histogramGaugesEnabled = histogramGaugesEnabled;
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
    DistributionStatisticConfigModifier modifier =
        histogramGaugesEnabled
            ? DistributionStatisticConfigModifier.IDENTITY
            : DistributionStatisticConfigModifier.DISABLE_HISTOGRAM_GAUGES;

    return new OpenTelemetryMeterRegistry(
        clock,
        baseTimeUnit,
        namingConvention,
        modifier,
        openTelemetry.getMeterProvider().get(INSTRUMENTATION_NAME));
  }
}
