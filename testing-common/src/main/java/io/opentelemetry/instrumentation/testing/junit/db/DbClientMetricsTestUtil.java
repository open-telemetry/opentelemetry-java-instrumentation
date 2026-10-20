/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public class DbClientMetricsTestUtil {

  private DbClientMetricsTestUtil() {}

  public static void assertDurationMetric(
      InstrumentationExtension testing,
      String instrumentationName,
      AttributeKey<?>... expectedKeys) {
    // db.system.name is required - see
    // https://opentelemetry.io/docs/specs/semconv/database/database-metrics/#metric-dbclientoperationduration
    assertThat(expectedKeys).extracting(AttributeKey::getKey).contains(DB_SYSTEM_NAME.getKey());
    testing.waitAndAssertAnyHistogramPoints(
        instrumentationName,
        "db.client.operation.duration",
        metric -> metric.hasDescription("Duration of database client operations.").hasUnit("s"),
        point ->
            point.hasAttributesSatisfying(
                attributes -> assertThat(attributes.asMap()).containsOnlyKeys(expectedKeys)));
  }
}
