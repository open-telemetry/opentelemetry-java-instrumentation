/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;

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
        .satisfies(
            metric ->
                assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                    .isEqualTo(
                        emitStableDatabaseSemconv() ? SchemaUrls.V1_44_0 : SchemaUrls.V1_24_0));
  }
}
