/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oshi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.testing.InMemoryMetricReader;
import io.opentelemetry.sdk.testing.assertj.metrics.MetricAssertions;
import io.opentelemetry.sdk.testing.assertj.metrics.MetricDataAssert;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeAll;

class AbstractMetricsTest {

  static SdkMeterProvider meterProvider;
  static InMemoryMetricReader metricReader;

  @BeforeAll
  static void initializeOpenTelemetry() {
    metricReader = InMemoryMetricReader.create();
    meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).buildAndRegisterGlobal();
  }

  protected void waitAndAssertMetrics(Consumer<MetricDataAssert>... assertions) {
    await()
        .untilAsserted(
            () -> {
              Collection<MetricData> metrics = metricReader.collectAllMetrics();

              assertThat(metrics).isNotEmpty();

              for (Consumer<MetricDataAssert> assertion : assertions) {
                assertThat(metrics)
                    .anySatisfy(metric -> assertion.accept(MetricAssertions.assertThat(metric)));
              }
            });
  }
}
