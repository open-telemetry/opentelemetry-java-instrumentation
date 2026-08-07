/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterProvider;
import java.util.function.Predicate;

final class FilteringMeter implements Meter {

  private static final Meter NOOP_METER = MeterProvider.noop().get("noop");

  private final Meter delegate;
  private final Predicate<String> metricNamePredicate;

  FilteringMeter(Meter delegate, Predicate<String> metricNamePredicate) {
    this.delegate = delegate;
    this.metricNamePredicate = metricNamePredicate;
  }

  @Override
  public LongCounterBuilder counterBuilder(String name) {
    return meter(name).counterBuilder(name);
  }

  @Override
  public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
    return meter(name).upDownCounterBuilder(name);
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    return meter(name).histogramBuilder(name);
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    return meter(name).gaugeBuilder(name);
  }

  private Meter meter(String name) {
    return metricNamePredicate.test(name) ? delegate : NOOP_METER;
  }
}
