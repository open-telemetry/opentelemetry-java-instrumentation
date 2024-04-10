/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static java.util.Collections.emptyList;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

final class HistogramAdviceUtil {

  static void setExplicitBucketsIfConfigured(
      DoubleHistogramBuilder builder, DistributionStatisticConfig config) {
    setExplicitBucketsIfConfigured(builder, config, null);
  }

  static void setExplicitBucketsIfConfigured(
      DoubleHistogramBuilder builder,
      DistributionStatisticConfig config,
      @Nullable TimeUnit timeUnit) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    NavigableSet<Double> buckets = config.getHistogramBuckets(false);
    ExtendedDoubleHistogramBuilder extendedBuilder = (ExtendedDoubleHistogramBuilder) builder;
    extendedBuilder.setExplicitBucketBoundariesAdvice(computeBuckets(buckets, timeUnit));
  }

  private static List<Double> computeBuckets(
      NavigableSet<Double> buckets, @Nullable TimeUnit timeUnit) {
    if (buckets.isEmpty()) {
      return emptyList();
    }
    // micrometer Timers always specify buckets in nanoseconds, we need to convert them to base unit
    double timeUnitMultiplier = timeUnit == null ? 1.0 : TimeUtils.nanosToUnit(1, timeUnit);
    List<Double> result = new ArrayList<>(buckets.size());
    for (double b : buckets) {
      result.add(b * timeUnitMultiplier);
    }
    return result;
  }

  private HistogramAdviceUtil() {}
}
