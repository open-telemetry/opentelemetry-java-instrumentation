/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OtlpLogExporterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, OtlpLoggerExporterAutoConfiguration.class));

  @Test
  void otlpEnabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "otelOtlpGrpcLogRecordExporter", OtlpGrpcLogRecordExporter.class))
                    .isNotNull());
  }

  @Test
  void otlpLogsEnabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.logs.enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "otelOtlpGrpcLogRecordExporter", OtlpGrpcLogRecordExporter.class))
                    .isNotNull());
  }

  @Test
  void otlpDisabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.enabled=false")
        .run(
            context -> assertThat(context.containsBean("otelOtlpGrpcLogRecordExporter")).isFalse());
  }

  @Test
  void otlpLogsDisabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.logs.enabled=false")
        .run(
            context -> assertThat(context.containsBean("otelOtlpGrpcLogRecordExporter")).isFalse());
  }

  @Test
  void loggerPresentByDefault() {
    runner.run(
        context ->
            assertThat(
                    context.getBean(
                        "otelOtlpGrpcLogRecordExporter", OtlpGrpcLogRecordExporter.class))
                .isNotNull());
  }
}
