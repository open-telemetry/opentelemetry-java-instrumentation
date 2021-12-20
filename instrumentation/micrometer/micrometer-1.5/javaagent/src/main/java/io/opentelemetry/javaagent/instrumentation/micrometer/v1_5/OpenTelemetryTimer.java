/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.toAttributes;

import io.micrometer.core.instrument.AbstractTimer;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.NoopHistogram;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

final class OpenTelemetryTimer extends AbstractTimer implements RemovableMeter {

  // TODO: use bound instruments when they're available
  private final LongHistogram otelHistogram;
  private final Attributes attributes;
  private final Measurements measurements;

  private volatile boolean removed = false;

  OpenTelemetryTimer(
      Id id,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      PauseDetector pauseDetector,
      Meter otelMeter) {
    super(id, clock, distributionStatisticConfig, pauseDetector, TimeUnit.MILLISECONDS, false);

    this.otelHistogram =
        otelMeter
            .histogramBuilder(id.getName())
            .setDescription(description(id))
            .setUnit("ms")
            .ofLongs()
            .build();
    this.attributes = toAttributes(id.getTags());

    if (isUsingMicrometerHistograms()) {
      measurements = new MicrometerHistogramMeasurements(clock, distributionStatisticConfig);
    } else {
      measurements = NoopMeasurements.INSTANCE;
    }
  }

  boolean isUsingMicrometerHistograms() {
    return histogram != NoopHistogram.INSTANCE;
  }

  @Override
  protected void recordNonNegative(long amount, TimeUnit unit) {
    if (amount >= 0 && !removed) {
      otelHistogram.record(unit.toMillis(amount), attributes);
      measurements.record(amount, unit);
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
    return measurements.max(unit);
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public void onRemove() {
    removed = true;
  }

  private interface Measurements {
    void record(long amount, TimeUnit unit);

    long count();

    double totalTime(TimeUnit unit);

    double max(TimeUnit unit);
  }

  // if micrometer histograms are not being used then there's no need to keep any local state
  // OpenTelemetry metrics bridge does not support reading measurements
  enum NoopMeasurements implements Measurements {
    INSTANCE;

    @Override
    public void record(long amount, TimeUnit unit) {}

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

    @Override
    public double max(TimeUnit unit) {
      UnsupportedReadLogger.logWarning();
      return Double.NaN;
    }
  }

  // calculate count, totalTime and max value for the use of micrometer histograms
  // kinda similar to how DropwizardTimer does that
  private static final class MicrometerHistogramMeasurements implements Measurements {

    private final LongAdder count = new LongAdder();
    private final LongAdder totalTime = new LongAdder();
    private final TimeWindowMax max;

    MicrometerHistogramMeasurements(
        Clock clock, DistributionStatisticConfig distributionStatisticConfig) {
      this.max = new TimeWindowMax(clock, distributionStatisticConfig);
    }

    @Override
    public void record(long amount, TimeUnit unit) {
      long nanos = unit.toNanos(amount);
      count.increment();
      totalTime.add(nanos);
      max.record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public long count() {
      return count.sum();
    }

    @Override
    public double totalTime(TimeUnit unit) {
      return TimeUtils.nanosToUnit(totalTime.sum(), unit);
    }

    @Override
    public double max(TimeUnit unit) {
      return max.poll(unit);
    }
  }
}
