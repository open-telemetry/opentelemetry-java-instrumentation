/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import java.util.List;
import java.util.concurrent.TimeUnit;

final class HttpMetricsUtil {

  // we'll use the old unit if the old semconv is in use
  static final boolean emitNewSemconvMetrics =
      SemconvStability.emitStableHttpSemconv() && !SemconvStability.emitOldHttpSemconv();

  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(
              0.0, 0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5,
              10.0));

  private static final double NANOS_PER_MS = TimeUnit.MILLISECONDS.toNanos(1);
  private static final double NANOS_PER_S = TimeUnit.SECONDS.toNanos(1);

  static DoubleHistogram createDurationHistogram(Meter meter, String name, String description) {
    DoubleHistogramBuilder durationBuilder =
        meter
            .histogramBuilder(name)
            .setUnit(emitNewSemconvMetrics ? "s" : "ms")
            .setDescription(description);
    // don't set custom buckets if milliseconds are still used
    if (emitNewSemconvMetrics && durationBuilder instanceof ExtendedDoubleHistogramBuilder) {
      ((ExtendedDoubleHistogramBuilder) durationBuilder)
          .setAdvice(advice -> advice.setExplicitBucketBoundaries(DURATION_SECONDS_BUCKETS));
    }
    return durationBuilder.build();
  }

  static double nanosToUnit(long durationNanos) {
    return durationNanos / (emitNewSemconvMetrics ? NANOS_PER_S : NANOS_PER_MS);
  }

  private HttpMetricsUtil() {}
}
