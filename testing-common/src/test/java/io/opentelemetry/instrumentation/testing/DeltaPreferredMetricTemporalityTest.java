/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DeltaPreferredMetricTemporalityTest {

  @RegisterExtension
  static final LibraryInstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void usesDeltaPreferredTemporalityForExportedMetrics() {
    Meter meter = testing.getOpenTelemetry().getMeterProvider().meterBuilder("test").build();
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
      List<MetricData> firstCollection = testing.metrics();
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

      assertThat(observableUpDownCounterValues(firstCollection)).containsExactly(1L);
      List<MetricData> secondCollection = testing.metrics();
      assertThat(observableUpDownCounterValues(secondCollection)).containsExactly(1L, 1L);

      activeTasks.set(0);
      List<MetricData> thirdCollection = testing.metrics();
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
}
