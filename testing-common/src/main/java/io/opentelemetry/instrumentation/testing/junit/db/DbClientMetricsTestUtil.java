/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public class DbClientMetricsTestUtil {

  private DbClientMetricsTestUtil() {}

  public static void assertDurationMetric(
      InstrumentationExtension testing,
      String instrumentationName,
      AttributeKey<?>... expectedKeys) {
    if (!emitStableDatabaseSemconv()) {
      return;
    }
    testing.waitAndAssertMetrics(
        instrumentationName,
        metrics ->
            metrics
                .hasName("db.client.operation.duration")
                .hasUnit("s")
                .hasDescription("Duration of database client operations.")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point.hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes.asMap())
                                            .containsOnlyKeys(expectedKeys)))));
  }
}
