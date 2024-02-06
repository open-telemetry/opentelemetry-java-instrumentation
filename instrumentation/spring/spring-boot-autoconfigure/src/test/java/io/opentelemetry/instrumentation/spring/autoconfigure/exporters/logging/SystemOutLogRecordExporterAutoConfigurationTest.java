/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link SystemOutLogRecordExporter}. */
class SystemOutLogRecordExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class,
                  SystemOutLogRecordExporterAutoConfiguration.class));

  @Test
  void loggingEnabledNew() {
    contextRunner
        .withPropertyValues("otel.logs.exporter=logging")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "otelSystemOutLogRecordExporter", SystemOutLogRecordExporter.class))
                    .isNotNull());
  }

  @Test
  @DisplayName("when exporters are ENABLED should initialize SystemOutLogRecordExporter bean")
  void loggingEnabled() {
    contextRunner
        .withPropertyValues("otel.exporter.logging.enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "otelSystemOutLogRecordExporter", SystemOutLogRecordExporter.class))
                    .isNotNull());
  }

  @Test
  void loggingLogsEnabled() {
    contextRunner
        .withPropertyValues("otel.exporter.logging.logs.enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "otelSystemOutLogRecordExporter", SystemOutLogRecordExporter.class))
                    .isNotNull());
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize SystemOutLogRecordExporter bean")
  void loggingDisabled() {
    contextRunner
        .withPropertyValues("otel.exporter.logging.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("otelSystemOutLogRecordExporter")).isFalse());
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize SystemOutLogRecordExporter bean")
  void loggingLogsDisabled() {
    contextRunner
        .withPropertyValues("otel.exporter.logging.logs.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("otelSystemOutLogRecordExporter")).isFalse());
  }

  @Test
  @DisplayName(
      "when exporter enabled property is MISSING should initialize SystemOutLogRecordExporter bean")
  void exporterPresentByDefault() {
    contextRunner.run(
        context -> assertThat(context.containsBean("otelSystemOutLogRecordExporter")).isFalse());
  }
}
