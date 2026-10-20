/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics.POOL_NAME;
import static io.opentelemetry.instrumentation.testing.util.InstrumentationScopeAssertions.hasScopeSchemaUrl;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longSum;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.SchemaUrls;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class DbConnectionPoolMetricsTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void poolNameOverridesAdditionalAttributes() {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            OpenTelemetry.noop(),
            "test",
            "argument-pool",
            Attributes.of(POOL_NAME, "attribute-pool"));

    assertThat(metrics.getAttributes().get(POOL_NAME)).isEqualTo("argument-pool");
  }

  @Test
  void shouldSetSchemaUrl() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, "test", "test-pool");
    metrics.connectionTimeouts().add(1);

    assertThat(metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0));
  }

  @Test
  void shouldExportPoolMetrics() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder().setMeterProvider(meterProvider).build();

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(
            openTelemetry, "test", "test-pool", Attributes.of(DB_NAMESPACE, "potatoes"));
    metrics.connectionTimeouts().add(1, metrics.getAttributes());
    metrics.connectionCreateTime().record(0.025, metrics.getAttributes());

    assertThat(metricReader.collectAllMetrics())
        .satisfiesExactlyInAnyOrder(
            metric ->
                longSum(
                        metricAssert ->
                            metricAssert
                                .hasName("db.client.connection.timeouts")
                                .hasUnit("{timeout}")
                                .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0)),
                        point ->
                            point
                                .hasValue(1)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(DB_CLIENT_CONNECTION_POOL_NAME, "test-pool"),
                                    equalTo(DB_NAMESPACE, "potatoes")))
                    .accept(assertThat(metric)),
            metric ->
                histogram(
                        metricAssert ->
                            metricAssert
                                .hasName("db.client.connection.create_time")
                                .hasUnit("s")
                                .satisfies(hasScopeSchemaUrl(SchemaUrls.V1_44_0)),
                        point ->
                            point
                                .hasSum(0.025)
                                .hasAttributesSatisfyingExactly(
                                    equalTo(DB_CLIENT_CONNECTION_POOL_NAME, "test-pool"),
                                    equalTo(DB_NAMESPACE, "potatoes")))
                    .accept(assertThat(metric)));
  }
}
