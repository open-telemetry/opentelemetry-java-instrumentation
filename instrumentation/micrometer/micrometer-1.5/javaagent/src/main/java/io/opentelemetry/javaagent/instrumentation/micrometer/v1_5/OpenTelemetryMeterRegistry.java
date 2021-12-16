/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

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
import io.opentelemetry.api.GlobalOpenTelemetry;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

public final class OpenTelemetryMeterRegistry extends MeterRegistry {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.micrometer-1.5";

  public static MeterRegistry create() {
    OpenTelemetryMeterRegistry openTelemetryMeterRegistry =
        new OpenTelemetryMeterRegistry(
            Clock.SYSTEM, GlobalOpenTelemetry.get().getMeterProvider().get(INSTRUMENTATION_NAME));
    openTelemetryMeterRegistry.config().onMeterRemoved(OpenTelemetryMeterRegistry::onMeterRemoved);
    return openTelemetryMeterRegistry;
  }

  private final io.opentelemetry.api.metrics.Meter otelMeter;

  private OpenTelemetryMeterRegistry(Clock clock, io.opentelemetry.api.metrics.Meter otelMeter) {
    super(clock);
    this.otelMeter = otelMeter;
  }

  @Override
  protected <T> Gauge newGauge(Meter.Id id, T t, ToDoubleFunction<T> toDoubleFunction) {
    return new OpenTelemetryGauge<>(id, t, toDoubleFunction, otelMeter);
  }

  @Override
  protected Counter newCounter(Meter.Id id) {
    return new OpenTelemetryCounter(id, otelMeter);
  }

  @Override
  protected LongTaskTimer newLongTaskTimer(
      Meter.Id id, DistributionStatisticConfig distributionStatisticConfig) {
    throw new UnsupportedOperationException("Not implemented yet");
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
      Meter.Id id, DistributionStatisticConfig distributionStatisticConfig, double v) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  protected Meter newMeter(Meter.Id id, Meter.Type type, Iterable<Measurement> iterable) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  protected <T> FunctionTimer newFunctionTimer(
      Meter.Id id,
      T t,
      ToLongFunction<T> toLongFunction,
      ToDoubleFunction<T> toDoubleFunction,
      TimeUnit timeUnit) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  protected <T> FunctionCounter newFunctionCounter(
      Meter.Id id, T t, ToDoubleFunction<T> toDoubleFunction) {
    throw new UnsupportedOperationException("Not implemented yet");
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
