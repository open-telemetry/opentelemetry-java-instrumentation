/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.DoublePointData;
import io.opentelemetry.sdk.metrics.data.DoubleSummaryPointData;
import io.opentelemetry.sdk.metrics.data.LongPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.metrics.export.IntervalMetricReader;
import io.opentelemetry.sdk.metrics.export.MetricExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

class AbstractMetricsTest {
  TestMetricExporter testMetricExporter;

  static SdkMeterProvider meterProvider;

  @BeforeAll
  static void initializeOpenTelemetry() {
    meterProvider = SdkMeterProvider.builder().buildAndRegisterGlobal();
  }

  @BeforeEach
  void beforeEach() {
    testMetricExporter = new TestMetricExporter();
  }

  IntervalMetricReader createIntervalMetricReader() {
    return IntervalMetricReader.builder()
        .setExportIntervalMillis(100)
        .setMetricExporter(testMetricExporter)
        .setMetricProducers(Collections.singletonList(meterProvider))
        .build();
  }

  public void verify(
      String metricName, String unit, MetricDataType type, boolean checkNonZeroValue) {
    List<MetricData> metricDataList = testMetricExporter.metricDataList;
    for (MetricData metricData : metricDataList) {
      if (metricData.getName().equals(metricName)) {
        assertThat(metricData.getDescription()).isNotEmpty();
        assertThat(metricData.getUnit()).isEqualTo(unit);
        metricData.getDoubleGaugeData().getPoints();
        List<PointData> points = new ArrayList<>();
        points.addAll(metricData.getDoubleGaugeData().getPoints());
        points.addAll(metricData.getDoubleSumData().getPoints());
        points.addAll(metricData.getDoubleSummaryData().getPoints());
        points.addAll(metricData.getLongGaugeData().getPoints());
        points.addAll(metricData.getLongSumData().getPoints());

        assertThat(points).isNotEmpty();
        assertThat(metricData.getType()).isEqualTo(type);
        if (checkNonZeroValue) {
          for (PointData point : points) {
            if (point instanceof LongPointData) {
              LongPointData longPoint = (LongPointData) point;
              assertThat(longPoint.getValue()).isGreaterThan(0);
            } else if (point instanceof DoublePointData) {
              DoublePointData doublePoint = (DoublePointData) point;
              assertThat(doublePoint.getValue()).isGreaterThan(0.0);
            } else if (point instanceof DoubleSummaryPointData) {
              DoubleSummaryPointData summaryPoint = (DoubleSummaryPointData) point;
              assertThat(summaryPoint.getSum()).isGreaterThan(0.0);
            } else {
              Assertions.fail("unexpected type " + metricData.getType());
            }
          }
        }
        return;
      }
    }
    Assertions.fail("No metric for " + metricName);
  }

  static class TestMetricExporter implements MetricExporter {
    private final List<MetricData> metricDataList = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public CompletableResultCode export(Collection<MetricData> collection) {
      metricDataList.addAll(collection);
      latch.countDown();
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      return CompletableResultCode.ofSuccess();
    }

    public void waitForData() throws InterruptedException {
      latch.await(1, TimeUnit.SECONDS);
    }
  }
}
