/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.statisticInstrumentName;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;
import static io.opentelemetry.instrumentation.micrometer.v1_5.TimeUnitHelper.getUnitString;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.NoopHistogram;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

final class OpenTelemetryTimer extends AbstractTimer implements RemovableMeter {

  private final Measurements measurements;
  private final TimeWindowMax max;
  private final TimeUnit baseTimeUnit;
  // TODO: use bound instruments when they're available
  private final DoubleHistogram otelHistogram;
  private final Attributes attributes;
  private final AsyncMeasurementHandle maxHandle;

  private volatile boolean removed = false;

  OpenTelemetryTimer(
      Id id,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector,
      TimeUnit baseTimeUnit,
      Meter otelMeter,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    super(id, clock, distributionStatisticConfig, pauseDetector, TimeUnit.MILLISECONDS, false);

    if (isUsingMicrometerHistograms()) {
      measurements = new MicrometerHistogramMeasurements();
    } else {
      measurements = NoopMeasurements.INSTANCE;
    }
    max = new TimeWindowMax(clock, distributionStatisticConfig);

    this.baseTimeUnit = baseTimeUnit;
    this.attributes = tagsAsAttributes(id);
    this.otelHistogram =
        otelMeter
            .histogramBuilder(id.getName())
            .setDescription(description(id))
            .setUnit(getUnitString(baseTimeUnit))
            .build();
    this.maxHandle =
        asyncInstrumentRegistry.buildGauge(
            statisticInstrumentName(id, Statistic.MAX),
            description(id),
            "ms",
            attributes,
            max,
            m -> m.poll(TimeUnit.MILLISECONDS));
  }

  boolean isUsingMicrometerHistograms() {
    return histogram != NoopHistogram.INSTANCE;
  }

  @Override
  protected void recordNonNegative(long amount, TimeUnit unit) {
    if (amount >= 0 && !removed) {
      long nanos = unit.toNanos(amount);
      double time = TimeUtils.nanosToUnit(nanos, baseTimeUnit);
      otelHistogram.record(time, attributes);
      measurements.record(nanos);
      max.record(nanos, TimeUnit.NANOSECONDS);
    }
  }

  @Override
  public long count() {
    return measurements.count();
  }

  @Override
  public double totalTime(TimeUnit unit) {
    return measurements.totalTime(unit);
  }

  @Override
  public double max(TimeUnit unit) {
    return max.poll(unit);
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public void onRemove() {
    removed = true;
    maxHandle.remove();
  }

  private interface Measurements {
    void record(long nanos);

    long count();

    double totalTime(TimeUnit unit);
  }

  // if micrometer histograms are not being used then there's no need to keep any local state
  // OpenTelemetry metrics bridge does not support reading measurements
  enum NoopMeasurements implements Measurements {
    INSTANCE;

    @Override
    public void record(long nanos) {}

    @Override
    public long count() {
      UnsupportedReadLogger.logWarning();
      return 0;
    }

    @Override
    public double totalTime(TimeUnit unit) {
      UnsupportedReadLogger.logWarning();
      return Double.NaN;
    }
  }

  // calculate count and totalTime value for the use of micrometer histograms
  // kinda similar to how DropwizardTimer does that
  private static final class MicrometerHistogramMeasurements implements Measurements {

    private final LongAdder count = new LongAdder();
    private final LongAdder totalTime = new LongAdder();

    @Override
    public void record(long nanos) {
      count.increment();
      totalTime.add(nanos);
    }

    @Override
    public long count() {
      return count.sum();
    }

    @Override
    public double totalTime(TimeUnit unit) {
      return TimeUtils.nanosToUnit(totalTime.sum(), unit);
    }
  }
}
