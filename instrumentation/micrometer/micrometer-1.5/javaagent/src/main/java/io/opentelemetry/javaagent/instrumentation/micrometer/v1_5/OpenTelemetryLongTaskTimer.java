/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.statisticInstrumentName;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryLongTaskTimer extends DefaultLongTaskTimer implements RemovableMeter {

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);

  private final DistributionStatisticConfig distributionStatisticConfig;
  // TODO: use bound instruments when they're available
  private final DoubleHistogram otelHistogram;
  private final LongUpDownCounter otelActiveTasksCounter;
  private final Attributes attributes;

  private volatile boolean removed = false;

  OpenTelemetryLongTaskTimer(
      Id id,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      Meter otelMeter) {
    super(id, clock, TimeUnit.MILLISECONDS, distributionStatisticConfig, false);
    this.distributionStatisticConfig = distributionStatisticConfig;

    this.otelHistogram =
        otelMeter
            .histogramBuilder(id.getName())
            .setDescription(description(id))
            .setUnit("ms")
            .build();
    this.otelActiveTasksCounter =
        otelMeter
            .upDownCounterBuilder(statisticInstrumentName(id, Statistic.ACTIVE_TASKS))
            .setDescription(description(id))
            .setUnit("tasks")
            .build();
    this.attributes = tagsAsAttributes(id);
  }

  @Override
  public Sample start() {
    Sample original = super.start();
    if (removed) {
      return original;
    }

    otelActiveTasksCounter.add(1, attributes);
    return new OpenTelemetrySample(original);
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

  boolean isUsingMicrometerHistograms() {
    return distributionStatisticConfig.isPublishingPercentiles()
        || distributionStatisticConfig.isPublishingHistogram();
  }

  private final class OpenTelemetrySample extends Sample {

    private final Sample original;
    private volatile boolean stopped = false;

    private OpenTelemetrySample(Sample original) {
      this.original = original;
    }

    @Override
    public long stop() {
      if (stopped) {
        return -1;
      }
      stopped = true;
      long durationNanos = original.stop();
      if (!removed) {
        otelActiveTasksCounter.add(-1, attributes);
        double time = durationNanos / NANOS_PER_MS;
        otelHistogram.record(time, attributes);
      }
      return durationNanos;
    }

    @Override
    public double duration(TimeUnit unit) {
      return stopped ? -1 : original.duration(unit);
    }
  }
}
