/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.AbstractDistributionSummary;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.NoopHistogram;
import io.micrometer.core.instrument.distribution.TimeWindowMax;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.Collections;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;

final class OpenTelemetryDistributionSummary extends AbstractDistributionSummary
    implements DistributionSummary, RemovableMeter {

  private final Measurements measurements;
  private final TimeWindowMax max;
  // TODO: use bound instruments when they're available
  private final DoubleHistogram otelHistogram;
  private final Attributes attributes;
  private final AsyncMeasurementHandle maxHandle;

  private volatile boolean removed = false;

  OpenTelemetryDistributionSummary(
      Id id,
      NamingConvention namingConvention,
      Clock clock,
      DistributionStatisticConfig distributionStatisticConfig,
      double scale,
      Meter otelMeter,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    super(id, clock, distributionStatisticConfig, scale, false);

    if (isUsingMicrometerHistograms()) {
      measurements = new MicrometerHistogramMeasurements();
    } else {
      measurements = NoopMeasurements.INSTANCE;
    }
    max = new TimeWindowMax(clock, distributionStatisticConfig);

    this.attributes = tagsAsAttributes(id, namingConvention);

    String conventionName = name(id, namingConvention);
    this.otelHistogram =
        otelMeter
            .histogramBuilder(conventionName)
            .setDescription(description(conventionName, id))
            .setUnit(baseUnit(id))
            .build();
    this.maxHandle =
        asyncInstrumentRegistry.buildGauge(
            conventionName + ".max",
            description(conventionName, id),
            baseUnit(id),
            attributes,
            max,
            TimeWindowMax::poll);
  }

  boolean isUsingMicrometerHistograms() {
    return histogram != NoopHistogram.INSTANCE;
  }

  @Override
  protected void recordNonNegative(double amount) {
    if (amount >= 0 && !removed) {
      otelHistogram.record(amount, attributes);
      measurements.record(amount);
      max.record(amount);
    }
  }

  @Override
  public long count() {
    return measurements.count();
  }

  @Override
  public double totalAmount() {
    return measurements.totalAmount();
  }

  @Override
  public double max() {
    return max.poll();
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
    void record(double amount);

    long count();

    double totalAmount();
  }

  // if micrometer histograms are not being used then there's no need to keep any local state
  // OpenTelemetry metrics bridge does not support reading measurements
  enum NoopMeasurements implements Measurements {
    INSTANCE;

    @Override
    public void record(double amount) {}

    @Override
    public long count() {
      UnsupportedReadLogger.logWarning();
      return 0;
    }

    @Override
    public double totalAmount() {
      UnsupportedReadLogger.logWarning();
      return Double.NaN;
    }
  }

  // calculate count and totalAmount value for the use of micrometer histograms
  // kinda similar to how DropwizardDistributionSummary does that
  private static final class MicrometerHistogramMeasurements implements Measurements {

    private final LongAdder count = new LongAdder();
    private final DoubleAdder totalAmount = new DoubleAdder();

    @Override
    public void record(double amount) {
      count.increment();
      totalAmount.add(amount);
    }

    @Override
    public long count() {
      return count.sum();
    }

    @Override
    public double totalAmount() {
      return totalAmount.sum();
    }
  }
}
