/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.attributeEntry;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.internal.aggregator.ExplicitBucketHistogramUtils;
import java.util.concurrent.TimeUnit;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;

@SuppressWarnings("PreferJavaTimeOverload")
public abstract class AbstractTimerTest {

  static final double[] DEFAULT_BUCKETS =
      ExplicitBucketHistogramUtils.DEFAULT_HISTOGRAM_BUCKET_BOUNDARIES.stream()
          .mapToDouble(d -> d)
          .toArray();

  protected abstract InstrumentationExtension testing();

  @Test
  void testTimer() {
    // given
    Timer timer =
        Timer.builder("testTimer")
            .description("This is a test timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    timer.record(42, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasUnit("ms")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(42_000)
                                                .hasCount(1)
                                                .hasAttributes(attributeEntry("tag", "value"))
                                                .hasBucketBoundaries(DEFAULT_BUCKETS)))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testTimer.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDescription("This is a test timer")
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(42_000)
                                                .hasAttributes(attributeEntry("tag", "value"))))));

    // micrometer gauge histogram is not emitted
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testTimer.histogram", AbstractIterableAssert::isEmpty);

    // when
    Metrics.globalRegistry.remove(timer);
    testing().clearData();
    timer.record(12, TimeUnit.SECONDS);

    // then
    testing()
        .waitAndAssertMetrics(INSTRUMENTATION_NAME, "testTimer", AbstractIterableAssert::isEmpty);
  }

  @Test
  void testNanoPrecision() {
    // given
    Timer timer = Timer.builder("testNanoTimer").register(Metrics.globalRegistry);

    // when
    timer.record(1_234_000, TimeUnit.NANOSECONDS);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testNanoTimer",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasUnit("ms")
                            .hasHistogramSatisfying(
                                histogram ->
                                    histogram.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasSum(1.234)
                                                .hasCount(1)
                                                .hasAttributes(Attributes.empty())))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            "testNanoTimer.max",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(metric)
                            .hasDoubleGaugeSatisfying(
                                gauge ->
                                    gauge.hasPointsSatisfying(
                                        point ->
                                            point
                                                .hasValue(1.234)
                                                .hasAttributes(Attributes.empty())))));
  }
}
