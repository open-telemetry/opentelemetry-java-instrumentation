/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
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
  @DisplayName("when exporters are ENABLED should initialize OtlpHttpSpanExporter bean")
  void otlpEnabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpSpanExporter", OtlpHttpSpanExporter.class))
                    .isNotNull());
  }

  @Test
  void otlpTracesEnabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.traces.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelOtlpSpanExporter", OtlpHttpSpanExporter.class))
                    .isNotNull());
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize OtlpGrpcSpanExporter bean")
  void otlpDisabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpSpanExporter")).isFalse());
  }

  @Test
  void otlpTracesDisabledOld() {
    this.contextRunner
        .withPropertyValues("otel.exporter.otlp.traces.enabled=false")
        .run(context -> assertThat(context.containsBean("otelOtlpSpanExporter")).isFalse());
  }
}
