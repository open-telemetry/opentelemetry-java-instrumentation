/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import java.util.List;

final class HistogramAdviceUtil {

  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(
              0.0, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5,
              10.0));

  static void setHttpDurationBuckets(DoubleHistogramBuilder builder) {
    if (!(builder instanceof ExtendedDoubleHistogramBuilder)) {
      // that shouldn't really happen
      return;
    }
    ((ExtendedDoubleHistogramBuilder) builder)
        .setAdvice(advice -> advice.setExplicitBucketBoundaries(DURATION_SECONDS_BUCKETS));
  }

  private HistogramAdviceUtil() {}
}
