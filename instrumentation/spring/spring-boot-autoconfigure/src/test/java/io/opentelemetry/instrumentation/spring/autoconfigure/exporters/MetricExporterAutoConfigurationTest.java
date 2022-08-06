/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.otlp.metrics.OtlpGrpcMetricExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging.LoggingMetricExporterAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpMetricExporterAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MetricExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OtlpMetricExporterAutoConfiguration.class,
                  LoggingMetricExporterAutoConfiguration.class));

  @Test
  void defaultConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context.getBean("otelOtlpGrpcMetricExporter", OtlpGrpcMetricExporter.class))
              .as("OTLP exporter is enabled by default")
              .isNotNull();
          assertThat(context.containsBean("otelLoggingMetricExporter"))
              .as("Logging exporter is not created unless explicitly configured")
              .isFalse();
        });
  }

  @Test
  void loggingEnabledByConfiguration() {
    contextRunner
        .withPropertyValues("otel.exporter.logging.enabled=true")
        .run(
            context -> {
              assertThat(
                      context.getBean("otelOtlpGrpcMetricExporter", OtlpGrpcMetricExporter.class))
                  .as("OTLP exporter is present even with logging enabled")
                  .isNotNull();
              assertThat(context.getBean("otelLoggingMetricExporter", LoggingMetricExporter.class))
                  .as("Logging exporter is explicitly enabled")
                  .isNotNull();
            });
  }
}
