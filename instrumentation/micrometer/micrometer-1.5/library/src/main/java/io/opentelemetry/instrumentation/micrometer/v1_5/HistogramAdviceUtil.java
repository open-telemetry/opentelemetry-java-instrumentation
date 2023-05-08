/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static java.util.Collections.emptyList;

import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import java.util.ArrayList;
import java.util.List;

final class HistogramAdviceUtil {

  static void setExplicitBucketsIfConfigured(
      DoubleHistogramBuilder builder, DistributionStatisticConfig config, double unitMultiplier) {
    double[] buckets = config.getServiceLevelObjectiveBoundaries();
    if (buckets == null || !(builder instanceof ExtendedDoubleHistogramBuilder)) {
      return;
    }
    ExtendedDoubleHistogramBuilder extendedBuilder = (ExtendedDoubleHistogramBuilder) builder;
    extendedBuilder.setAdvice(
        advice -> advice.setExplicitBucketBoundaries(computeBuckets(buckets, unitMultiplier)));
  }

  private static List<Double> computeBuckets(double[] buckets, double unitMultiplier) {
    if (buckets.length == 0) {
      return emptyList();
    }
    List<Double> result = new ArrayList<>(buckets.length);
    for (double b : buckets) {
      result.add(b * unitMultiplier);
    }
    return result;
  }

  private HistogramAdviceUtil() {}
}
