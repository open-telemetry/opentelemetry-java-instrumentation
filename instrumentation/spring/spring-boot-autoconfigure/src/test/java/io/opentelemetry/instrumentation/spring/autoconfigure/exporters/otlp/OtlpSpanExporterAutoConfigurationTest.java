/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link OtlpSpanExporterAutoConfiguration}. */
class OtlpSpanExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, OtlpSpanExporterAutoConfiguration.class));

  @Test
  @DisplayName("when exporters are ENABLED should initialize OtlpGrpcSpanExporter bean")
  void otlpEnabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpGrpcSpanExporter", OtlpGrpcSpanExporter.class))
                    .isNotNull());
  }

  @Test
  void otlpTracesEnabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.traces.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpGrpcSpanExporter", OtlpGrpcSpanExporter.class))
                    .isNotNull());
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize OtlpGrpcSpanExporter bean")
  void otlpDisabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpGrpcSpanExporter")).isFalse());
  }

  @Test
  void otlpTracesDisabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.traces.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpGrpcSpanExporter")).isFalse());
  }

  @Test
  @DisplayName("when otlp enabled property is MISSING should initialize OtlpGrpcSpanExporter bean")
  void exporterPresentByDefault() {
    this.contextRunner.run(
        context ->
            assertThat(context.getBean("otelOtlpGrpcSpanExporter", OtlpGrpcSpanExporter.class))
                .isNotNull());
  }
}
