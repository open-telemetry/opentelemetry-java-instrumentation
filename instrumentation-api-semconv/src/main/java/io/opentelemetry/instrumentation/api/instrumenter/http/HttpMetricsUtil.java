/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.http;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.extension.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.semconv.SemanticAttributes;
import java.util.List;

final class HttpMetricsUtil {

  static final List<Double> DURATION_SECONDS_BUCKETS =
      unmodifiableList(
          asList(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0));

  static DoubleHistogramBuilder createStableDurationHistogramBuilder(
      Meter meter, String name, String description) {
    DoubleHistogramBuilder durationBuilder =
        meter.histogramBuilder(name).setUnit("s").setDescription(description);
    // don't set custom buckets if milliseconds are still used
    if (durationBuilder instanceof ExtendedDoubleHistogramBuilder) {
      ((ExtendedDoubleHistogramBuilder) durationBuilder)
          .setExplicitBucketBoundariesAdvice(DURATION_SECONDS_BUCKETS);
    }
    return durationBuilder;
  }

  static Attributes mergeClientAttributes(Attributes startAttributes, Attributes endAttributes) {
    AttributesBuilder builder = startAttributes.toBuilder().putAll(endAttributes);
    String url = startAttributes.get(SemanticAttributes.URL_FULL);
    if (url != null) {
      int index = url.indexOf("://");
      if (index > 0) {
        builder.put(SemanticAttributes.URL_SCHEME, url.substring(0, index));
      }
    }
    return builder.build();
  }

  private HttpMetricsUtil() {}
}
