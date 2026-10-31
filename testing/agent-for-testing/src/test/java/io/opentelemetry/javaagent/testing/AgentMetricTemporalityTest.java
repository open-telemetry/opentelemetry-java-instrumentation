/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentMetricTemporalityTest {

  @BeforeEach
  void reset() {
    AgentTestingExporterAccess.reset();
  }

  @Test
  void usesConfiguredTemporalityForEveryInstrument() {
    Meter meter = GlobalOpenTelemetry.get().getMeterProvider().meterBuilder("test").build();
    meter.counterBuilder("counter").build().add(1);
    ObservableLongCounter observableCounter =
        meter
            .counterBuilder("observable-counter")
            .buildWithCallback(measurement -> measurement.record(1));
    LongUpDownCounter upDownCounter = meter.upDownCounterBuilder("up-down-counter").build();
    upDownCounter.add(1);
    ObservableLongUpDownCounter observableUpDownCounter =
        meter
            .upDownCounterBuilder("observable-up-down-counter")
            .buildWithCallback(measurement -> measurement.record(1));
    DoubleHistogram histogram = meter.histogramBuilder("histogram").build();
    histogram.record(1);

    try {
      List<MetricData> metrics = AgentTestingExporterAccess.getExportedMetrics();
      assertThat(metric(metrics, "counter").getLongSumData().getAggregationTemporality())
          .isEqualTo(expectedTemporality(InstrumentType.COUNTER));
      assertThat(metric(metrics, "observable-counter").getLongSumData().getAggregationTemporality())
          .isEqualTo(expectedTemporality(InstrumentType.OBSERVABLE_COUNTER));
      assertThat(metric(metrics, "up-down-counter").getLongSumData().getAggregationTemporality())
          .isEqualTo(expectedTemporality(InstrumentType.UP_DOWN_COUNTER));
      assertThat(
              metric(metrics, "observable-up-down-counter")
                  .getLongSumData()
                  .getAggregationTemporality())
          .isEqualTo(expectedTemporality(InstrumentType.OBSERVABLE_UP_DOWN_COUNTER));
      assertThat(metric(metrics, "histogram").getHistogramData().getAggregationTemporality())
          .isEqualTo(expectedTemporality(InstrumentType.HISTOGRAM));
    } finally {
      observableCounter.close();
      observableUpDownCounter.close();
    }
  }

  private static AggregationTemporality expectedTemporality(InstrumentType instrumentType) {
    String override = System.getProperty("otel.javaagent.testing.exporter.temporality");
    if (override != null) {
      return AggregationTemporality.valueOf(override.toUpperCase(Locale.ROOT));
    }
    return AggregationTemporalitySelector.deltaPreferred()
        .getAggregationTemporality(instrumentType);
  }

  private static MetricData metric(List<MetricData> metrics, String name) {
    return metrics.stream()
        .filter(metric -> metric.getName().equals(name))
        .findFirst()
        .orElseThrow(AssertionError::new);
  }
}
