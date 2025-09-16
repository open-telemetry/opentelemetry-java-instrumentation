/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class MetricsAssert {

  @SafeVarargs
  public static void waitAndAssertMetrics(
      Supplier<List<MetricData>> metricData,
      String instrumentationName,
      Consumer<MetricAssert>... assertions) {
    await().untilAsserted(() -> assertMetrics(metricData.get(), instrumentationName, assertions));
  }

  @SafeVarargs
  public static void assertMetrics(
      List<MetricData> metricData,
      String instrumentationName,
      Consumer<MetricAssert>... assertions) {
    Collection<MetricData> metrics = instrumentationMetrics(instrumentationName, metricData);
    assertThat(metrics).isNotEmpty();
    for (int i = 0; i < assertions.length; i++) {
      int index = i;
      assertThat(metrics)
          .describedAs(
              "Metrics for instrumentation %s and assertion %d", instrumentationName, index)
          .anySatisfy(metric -> assertions[index].accept(assertThat(metric)));
    }
  }

  private static List<MetricData> instrumentationMetrics(
      String instrumentationName, List<MetricData> metrics) {
    return metrics.stream()
        .filter(m -> m.getInstrumentationScopeInfo().getName().equals(instrumentationName))
        .collect(Collectors.toList());
  }

  private MetricsAssert() {}
}
