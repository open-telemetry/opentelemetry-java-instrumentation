/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.util;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import java.util.function.Consumer;
import org.assertj.core.api.ThrowingConsumer;

/** Composable assertions on {@link MetricData}, intended to be passed to {@code satisfies(...)}. */
public final class MetricAssertions {

  /**
   * Asserts that the metric is a histogram whose points match exactly the given assertions, in any
   * order.
   *
   * <pre>{@code
   * assertThat(metric)
   *     .hasName("duration")
   *     .satisfies(hasHistogramPointsSatisfyingExactly(point -> point.hasCount(1)));
   * }</pre>
   */
  @SafeVarargs
  @SuppressWarnings("varargs")
  public static ThrowingConsumer<MetricData> hasHistogramPointsSatisfyingExactly(
      Consumer<HistogramPointAssert>... assertions) {
    return metric ->
        assertThat(metric)
            .hasHistogramSatisfying(histogram -> histogram.hasPointsSatisfying(assertions));
  }

  private MetricAssertions() {}
}
