/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.micrometer.v1_5.AbstractCounterTest.INSTRUMENTATION_NAME;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;

import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Metrics;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;

public abstract class AbstractLongTaskTimerTest {

  protected abstract InstrumentationExtension testing();

  @Test
  void testMetricsUseDeltaPreferredTemporality() {
    Meter meter = testing().getOpenTelemetry().getMeterProvider().meterBuilder("test").build();
    meter.counterBuilder("counter").build().add(1);
    ObservableLongCounter observableCounter =
        meter
            .counterBuilder("observable-counter")
            .buildWithCallback(measurement -> measurement.record(1));
    LongUpDownCounter upDownCounter = meter.upDownCounterBuilder("up-down-counter").build();
    upDownCounter.add(1);
    AtomicLong activeTasks = new AtomicLong(1);
    ObservableLongUpDownCounter observableUpDownCounter =
        meter
            .upDownCounterBuilder("observable-up-down-counter")
            .buildWithCallback(measurement -> measurement.record(activeTasks.get()));
    DoubleHistogram histogram = meter.histogramBuilder("histogram").build();
    histogram.record(1);

    try {
      List<MetricData> firstCollection = testing().metrics();
      assertThat(metric(firstCollection, "counter").getLongSumData().getAggregationTemporality())
          .isEqualTo(AggregationTemporality.DELTA);
      assertThat(
              metric(firstCollection, "observable-counter")
                  .getLongSumData()
                  .getAggregationTemporality())
          .isEqualTo(AggregationTemporality.DELTA);
      assertThat(
              metric(firstCollection, "up-down-counter")
                  .getLongSumData()
                  .getAggregationTemporality())
          .isEqualTo(AggregationTemporality.CUMULATIVE);
      assertThat(
              metric(firstCollection, "observable-up-down-counter")
                  .getLongSumData()
                  .getAggregationTemporality())
          .isEqualTo(AggregationTemporality.CUMULATIVE);
      assertThat(
              metric(firstCollection, "histogram").getHistogramData().getAggregationTemporality())
          .isEqualTo(AggregationTemporality.DELTA);
      assertThat(
              metric(firstCollection, "observable-up-down-counter")
                  .getLongSumData()
                  .getPoints()
                  .iterator()
                  .next()
                  .getValue())
          .isEqualTo(1);

      List<MetricData> secondCollection = testing().metrics();
      assertThat(observableUpDownCounterValues(secondCollection)).containsExactly(1L, 1L);

      activeTasks.set(0);
      List<MetricData> thirdCollection = testing().metrics();
      assertThat(observableUpDownCounterValues(thirdCollection)).containsExactly(1L, 1L, 0L);
    } finally {
      observableCounter.close();
      observableUpDownCounter.close();
    }
  }

  private static MetricData metric(List<MetricData> metrics, String name) {
    return metrics.stream()
        .filter(metric -> metric.getName().equals(name))
        .findFirst()
        .orElseThrow(AssertionError::new);
  }

  private static List<Long> observableUpDownCounterValues(List<MetricData> metrics) {
    return metrics.stream()
        .filter(metric -> metric.getName().equals("observable-up-down-counter"))
        .map(metric -> metric.getLongSumData().getPoints().iterator().next().getValue())
        .collect(toList());
  }

  @Test
  void testLongTaskTimer() throws InterruptedException {
    // given
    LongTaskTimer timer =
        LongTaskTimer.builder("testLongTaskTimer")
            .description("This is a test long task timer")
            .tags("tag", "value")
            .register(Metrics.globalRegistry);

    // when
    LongTaskTimer.Sample sample = timer.start();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testLongTaskTimer.active")
                    .hasDescription("This is a test long task timer")
                    .hasUnit("{tasks}")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.isNotMonotonic()
                                .hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasValue(1)
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("tag"), "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testLongTaskTimer.duration")
                    .hasDescription("This is a test long task timer")
                    .hasUnit("s")
                    .hasDoubleSumSatisfying(
                        sum ->
                            sum.isNotMonotonic()
                                .hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasAttributesSatisfyingExactly(
                                                equalTo(stringKey("tag"), "value"))
                                            .satisfies(
                                                pointData ->
                                                    assertThat(pointData.getValue())
                                                        .isPositive()))));

    // when
    MILLISECONDS.sleep(100);
    sample.stop();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testLongTaskTimer.active")
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("tag"), "value")))));
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            metric ->
                metric
                    .hasName("testLongTaskTimer.duration")
                    .hasDoubleSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        .hasValue(0)
                                        .hasAttributesSatisfyingExactly(
                                            equalTo(stringKey("tag"), "value")))));

    // when timer is removed from the registry
    Metrics.globalRegistry.remove(timer);
    testing().clearData();
    timer.start();

    // then no tasks are active after starting a new sample
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "testLongTaskTimer.active", AbstractIterableAssert::isEmpty);
  }
}
