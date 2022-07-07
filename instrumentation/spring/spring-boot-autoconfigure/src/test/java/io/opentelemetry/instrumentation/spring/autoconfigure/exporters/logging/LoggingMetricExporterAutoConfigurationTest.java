/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoggingMetricExporterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class,
                  LoggingMetricExporterAutoConfiguration.class));

  @Test
  void loggingEnabled() {
    runner
        .withPropertyValues("otel.exporter.logging.enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean("otelLoggingMetricExporter", LoggingMetricExporter.class))
                    .isNotNull());
  }

  @Test
  void loggingMetricsEnabled() {
    runner
        .withPropertyValues("otel.exporter.logging.metrics.enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean("otelLoggingMetricExporter", LoggingMetricExporter.class))
                    .isNotNull());
  }

  @Test
  void loggingDisabled() {
    runner
        .withPropertyValues("otel.exporter.logging.enabled=false")
        .run(context -> assertThat(context.containsBean("otelLoggingMetricExporter")).isFalse());
  }

  @Test
  void loggingMetricsDisabled() {
    runner
        .withPropertyValues("otel.exporter.logging.metrics.enabled=false")
        .run(context -> assertThat(context.containsBean("otelLoggingMetricExporter")).isFalse());
  }

  @Test
  void noProperties() {
    runner.run(
        context ->
            assertThat(context.getBean("otelLoggingMetricExporter", LoggingMetricExporter.class))
                .isNotNull());
  }
}
