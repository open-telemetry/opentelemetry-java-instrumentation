/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class InstrumentationTestRunnerTest {

  @Test
  void requiresExactlyOneMetricBeforeAssertions() {
    MetricData metric =
        ImmutableMetricData.createLongGauge(
            Resource.empty(),
            InstrumentationScopeInfo.create("test"),
            "duration",
            "",
            "",
            ImmutableGaugeData.empty());
    InstrumentationTestRunner runner = new FixedMetricsTestRunner(asList(metric, metric));
    AtomicBoolean metricAssertionCalled = new AtomicBoolean();
    AtomicBoolean pointAssertionCalled = new AtomicBoolean();

    Awaitility.setDefaultPollDelay(Duration.ZERO);
    Awaitility.setDefaultPollInterval(Duration.ofMillis(1));
    Awaitility.setDefaultTimeout(Duration.ofMillis(10));
    try {
      assertThatThrownBy(
              () ->
                  runner.waitAndAssertHistogramPoints(
                      "test",
                      "duration",
                      metricAssert -> {
                        metricAssertionCalled.set(true);
                        throw new IllegalStateException();
                      },
                      pointAssert -> pointAssertionCalled.set(true)))
          .isInstanceOf(AssertionError.class);
    } finally {
      Awaitility.reset();
    }

    assertThat(metricAssertionCalled).isFalse();
    assertThat(pointAssertionCalled).isFalse();
  }

  private static class FixedMetricsTestRunner extends InstrumentationTestRunner {

    private final List<MetricData> metrics;

    private FixedMetricsTestRunner(List<MetricData> metrics) {
      super(OpenTelemetry.noop());
      this.metrics = metrics;
    }

    @Override
    public void beforeTestClass() {}

    @Override
    public void afterTestClass() throws IOException {}

    @Override
    public void clearAllExportedData() {}

    @Override
    public OpenTelemetry getOpenTelemetry() {
      return OpenTelemetry.noop();
    }

    @Override
    public List<SpanData> getExportedSpans() {
      return emptyList();
    }

    @Override
    public List<MetricData> getExportedMetrics() {
      return metrics;
    }

    @Override
    public List<LogRecordData> getExportedLogRecords() {
      return emptyList();
    }

    @Override
    public boolean forceFlushCalled() {
      return false;
    }
  }
}
