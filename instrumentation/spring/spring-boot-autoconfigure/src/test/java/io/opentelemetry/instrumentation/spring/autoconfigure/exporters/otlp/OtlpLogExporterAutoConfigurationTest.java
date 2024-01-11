/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import org.junit.jupiter.api.DisplayName;
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
                assertThat(context.getBean("otelOtlpLogRecordExporter", LogRecordExporter.class))
                    .isNotNull());
  }

  @Test
  void otlpLogsEnabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.logs.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpLogRecordExporter", LogRecordExporter.class))
                    .isNotNull());
  }

  @Test
  void otlpDisabled() {
    runner
        .withPropertyValues("otel.exporter.otlp.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpLogRecordExporter")).isFalse());
  }

  @Test
  void otlpLogsDisabledOld() {
    runner
        .withPropertyValues("otel.exporter.otlp.logs.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpLogRecordExporter")).isFalse());
  }

  @Test
  void otlpLogsDisabled() {
    runner
        .withPropertyValues("otel.logs.exporter=none")
        .run(context -> assertThat(context.containsBean("otelOtlpLogRecordExporter")).isFalse());
  }

  @Test
  void otlpHttpUsedByDefault() {
    runner.run(
        context ->
            assertThat(
                    context.getBean("otelOtlpLogRecordExporter", OtlpHttpLogRecordExporter.class))
                .isNotNull());
  }

  @Test
  @DisplayName("use grpc when protocol set")
  void useGrpc() {
    runner
        .withPropertyValues("otel.exporter.otlp.protocol=grpc")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "otelOtlpLogRecordExporter", OtlpGrpcLogRecordExporter.class))
                    .isNotNull());
  }

  @Test
  @DisplayName("use http when unknown protocol set")
  void useHttpWhenAnUnknownProtocolIsSet() {
    runner
        .withPropertyValues("otel.exporter.otlp.protocol=unknown")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "otelOtlpLogRecordExporter", OtlpHttpLogRecordExporter.class))
                    .isNotNull());
  }
}
