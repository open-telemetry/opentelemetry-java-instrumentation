/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.Arrays;

public class DbClientMetricsTestUtil {

  private DbClientMetricsTestUtil() {}

  public static void assertDurationMetric(
      InstrumentationExtension testing,
      String instrumentationName,
      AttributeKey<?>... expectedKeys) {
    // db.system.name is required - see
    // https://opentelemetry.io/docs/specs/semconv/database/database-metrics/#metric-dbclientoperationduration
    assertThat(expectedKeys).extracting(AttributeKey::getKey).contains(DB_SYSTEM_NAME.getKey());
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

  public static void assertDurationMetricWithResponseStatus(
      InstrumentationExtension testing,
      String instrumentationName,
      String expectedResponseStatusCode,
      AttributeKey<?>... expectedKeys) {
    // db.system.name is required - see
    // https://opentelemetry.io/docs/specs/semconv/database/database-metrics/#metric-dbclientoperationduration
    assertThat(expectedKeys).extracting(AttributeKey::getKey).contains(DB_SYSTEM_NAME.getKey());
    AttributeKey<?>[] expectedKeysWithStatus = Arrays.copyOf(expectedKeys, expectedKeys.length + 1);
    expectedKeysWithStatus[expectedKeys.length] = DB_RESPONSE_STATUS_CODE;
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
                                            .containsOnlyKeys(expectedKeysWithStatus)
                                            .containsEntry(
                                                DB_RESPONSE_STATUS_CODE, expectedResponseStatusCode)
                                            .doesNotContainKey(ERROR_TYPE)))));
  }
}
