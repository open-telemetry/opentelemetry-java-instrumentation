/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramGauges;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import javax.annotation.Nullable;

/**
 * A {@link MeterRegistry} implementation that forwards all the captured metrics to the {@linkplain
 * io.opentelemetry.api.metrics.Meter OpenTelemetry Meter} obtained from the passed {@link
 * OpenTelemetry} instance.
 */
public final class OpenTelemetryMeterRegistry extends MeterRegistry {

  /**
   * Returns a new {@link OpenTelemetryMeterRegistry} configured with the given {@link
   * OpenTelemetry}.
   */
  public static MeterRegistry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link OpenTelemetryMeterRegistryBuilder} configured with the given {@link
   * OpenTelemetry}.
   */
  public static OpenTelemetryMeterRegistryBuilder builder(OpenTelemetry openTelemetry) {
    return new OpenTelemetryMeterRegistryBuilder(openTelemetry);
  }

  private final io.opentelemetry.api.metrics.Meter otelMeter;
  private final AsyncInstrumentRegistry asyncInstrumentRegistry;

  OpenTelemetryMeterRegistry(Clock clock, io.opentelemetry.api.metrics.Meter otelMeter) {
    super(clock);
    this.otelMeter = otelMeter;
    this.asyncInstrumentRegistry = AsyncInstrumentRegistry.getOrCreate(otelMeter);
    this.config().onMeterRemoved(OpenTelemetryMeterRegistry::onMeterRemoved);
  }

  @Override
  protected <T> Gauge newGauge(Meter.Id id, @Nullable T obj, ToDoubleFunction<T> valueFunction) {
    return new OpenTelemetryGauge<>(id, obj, valueFunction, asyncInstrumentRegistry);
  }

  @Override
  protected Counter newCounter(Meter.Id id) {
    return new OpenTelemetryCounter(id, otelMeter);
  }

  @Override
  protected LongTaskTimer newLongTaskTimer(
      Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
    OpenTelemetryLongTaskTimer timer =
        new OpenTelemetryLongTaskTimer(id, clock, distributionStatisticConfig, otelMeter);
    if (timer.isUsingMicrometerHistograms()) {
      HistogramGauges.registerWithCommonFormat(timer, this);
    }
    return timer;
  }

  @Override
  protected Timer newTimer(
      Meter.Id id,
      DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector) {
    OpenTelemetryTimer timer =
        new OpenTelemetryTimer(id, clock, distributionStatisticConfig, pauseDetector, otelMeter);
    if (timer.isUsingMicrometerHistograms()) {
      HistogramGauges.registerWithCommonFormat(timer, this);
    }
    return timer;
  }

  @Override
  protected DistributionSummary newDistributionSummary(
      Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
    OpenTelemetryDistributionSummary distributionSummary =
        new OpenTelemetryDistributionSummary(
            id, clock, distributionStatisticConfig, scale, otelMeter);
    if (distributionSummary.isUsingMicrometerHistograms()) {
      HistogramGauges.registerWithCommonFormat(distributionSummary, this);
    }
    return distributionSummary;
  }

  @Override
  protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> measurements) {
    return new OpenTelemetryMeter(id, measurements, asyncInstrumentRegistry);
  }

  @Override
  protected <T> FunctionTimer newFunctionTimer(
      Meter.Id id,
      T obj,
      ToLongFunction<T> countFunction,
      ToDoubleFunction<T> totalTimeFunction,
      TimeUnit totalTimeFunctionUnit) {
    return new OpenTelemetryFunctionTimer<>(
        id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, asyncInstrumentRegistry);
  }

  @Override
  protected <T> FunctionCounter newFunctionCounter(
      Meter.Id id, T obj, ToDoubleFunction<T> countFunction) {
    return new OpenTelemetryFunctionCounter<>(id, obj, countFunction, asyncInstrumentRegistry);
  }

  @Override
  protected TimeUnit getBaseTimeUnit() {
    return TimeUnit.MILLISECONDS;
  }

  @Override
  protected DistributionStatisticConfig defaultHistogramConfig() {
    return DistributionStatisticConfig.DEFAULT;
  }

  private static void onMeterRemoved(Meter meter) {
    if (meter instanceof RemovableMeter) {
      ((RemovableMeter) meter).onRemove();
    }
  }
}
