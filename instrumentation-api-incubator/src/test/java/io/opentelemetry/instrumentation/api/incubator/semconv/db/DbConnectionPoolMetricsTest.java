/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.semconv.SchemaUrls;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DbConnectionPoolMetricsTest {

  @RegisterExtension final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @ParameterizedTest
  @ValueSource(ints = {0, 1})
  void shouldUseOpenTelemetryInstanceForSemconvSelection(int semconvVersion) {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    cleanup.deferCleanup(meterProvider);
    ExtendedOpenTelemetry openTelemetry = mock(ExtendedOpenTelemetry.class, RETURNS_DEEP_STUBS);
    DeclarativeConfigProperties generalConfig =
        mock(DeclarativeConfigProperties.class, RETURNS_DEEP_STUBS);
    when(openTelemetry.getMeterProvider()).thenReturn(meterProvider);
    when(openTelemetry.getGeneralInstrumentationConfig()).thenReturn(generalConfig);
    when(generalConfig.get("db").get("semconv").getInt("version")).thenReturn(semconvVersion);

    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(openTelemetry, "test", "test-pool");
    metrics.connectionTimeouts().add(1);

    assertThat(metricReader.collectAllMetrics())
        .singleElement()
        .satisfies(
            metric -> {
              assertThat(metric.getName())
                  .isEqualTo(
                      semconvVersion == 1
                          ? "db.client.connection.timeouts"
                          : "db.client.connections.timeouts");
              assertThat(metric.getInstrumentationScopeInfo().getSchemaUrl())
                  .isEqualTo(semconvVersion == 1 ? SchemaUrls.V1_44_0 : SchemaUrls.V1_24_0);
            });
  }
}
