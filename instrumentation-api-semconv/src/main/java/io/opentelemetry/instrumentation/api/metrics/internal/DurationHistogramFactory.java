/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.metrics.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class DurationHistogramFactory {
  public static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(
              0.0, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5,
              10.0));
  // we'll use the old unit if the old semconv is in use
  private static final boolean useSeconds =
      SemconvStability.emitStableHttpSemconv() && !SemconvStability.emitOldHttpSemconv();

  private DurationHistogramFactory() {}

  public static DurationHistogram create(Meter meter, String name, String description) {
    return create(meter.histogramBuilder(name).setDescription(description));
  }

  public static DurationHistogram create(DoubleHistogramBuilder builder) {
    // don't set custom buckets if milliseconds are still used
    if (useSeconds && builder instanceof ExtendedDoubleHistogramBuilder) {
      ((ExtendedDoubleHistogramBuilder) builder)
          .setAdvice(advice -> advice.setExplicitBucketBoundaries(DURATION_SECONDS_BUCKETS));
    }
    return new DurationHistogram(builder.setUnit(useSeconds ? "s" : "ms").build());
  }

  static double toUnit(long duration, TimeUnit unit) {
    return convertTimeUnit(
        (double) duration, unit, useSeconds ? TimeUnit.SECONDS : TimeUnit.MILLISECONDS);
  }

  private static double convertTimeUnit(double amount, TimeUnit from, TimeUnit to) {
    // if the same unit is passed, avoid the conversion
    if (from == to) {
      return amount;
    }
    // is from or to the larger unit?
    if (from.ordinal() < to.ordinal()) { // from is smaller
      return amount / from.convert(1, to);
    } else {
      return amount * to.convert(1, from);
    }
  }
}
