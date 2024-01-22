/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OtlpMetricExporterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, OtlpMetricExporterAutoConfiguration.class));

  @Test
  void otlpEnabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpMetricExporter", OtlpHttpMetricExporter.class))
                    .isNotNull());
  }

  @Test
  void otlpMetricsEnabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.metrics.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpMetricExporter", OtlpHttpMetricExporter.class))
                    .isNotNull());
  }

  @Test
  void otlpDisabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpMetricExporter")).isFalse());
  }

  @Test
  void otlpMetricsDisabledOld() {
    runner
        .withPropertyValues("otel.exporter.otlp.metrics.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpMetricExporter")).isFalse());
  }

  @Test
  void otlpMetricsDisabled() {
    runner
        .withPropertyValues("otel.metrics.exporter=none")
        .run(context -> assertThat(context.containsBean("otelOtlpMetricExporter")).isFalse());
  }
}
