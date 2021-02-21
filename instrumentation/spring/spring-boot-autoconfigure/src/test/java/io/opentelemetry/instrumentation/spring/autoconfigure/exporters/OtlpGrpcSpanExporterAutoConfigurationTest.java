/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpGrpcSpanExporterAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpGrpcSpanExporterProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link OtlpGrpcSpanExporterAutoConfiguration}. */
class OtlpGrpcSpanExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, OtlpGrpcSpanExporterAutoConfiguration.class));

  @Test
  @DisplayName("when exporters are ENABLED should initialize OtlpGrpcSpanExporter bean")
  void exportersEnabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporters.otlp.enabled=true")
        .run(
            (context) -> {
              assertThat(context.getBean("otelOtlpGrpcSpanExporter", OtlpGrpcSpanExporter.class))
                  .isNotNull();
            });
  }

  @AfterEach
  void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  @DisplayName(
      "when opentelemetry.trace.exporter.otlp properties are set should initialize OtlpGrpcSpanExporterProperties")
  void handlesProperties() {
    this.contextRunner
        .withPropertyValues(
            "opentelemetry.trace.exporter.otlp.enabled=true",
            "opentelemetry.trace.exporter.otlp.endpoint=http://localhost:8080/test",
            "opentelemetry.trace.exporter.otlp.spantimeout=69ms")
        .run(
            (context) -> {
              OtlpGrpcSpanExporterProperties otlpSpanExporterProperties =
                  context.getBean(OtlpGrpcSpanExporterProperties.class);
              assertThat(otlpSpanExporterProperties.getEndpoint())
                  .isEqualTo("http://localhost:8080/test");
              assertThat(otlpSpanExporterProperties.getSpanTimeout()).hasMillis(69);
            });
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize OtlpGrpcSpanExporter bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporter.otlp.enabled=false")
        .run(
            (context) -> {
              assertThat(context.containsBean("otelOtlpGrpcSpanExporter")).isFalse();
            });
  }

  @Test
  @DisplayName("when otlp enabled property is MISSING should initialize OtlpGrpcSpanExporter bean")
  void noProperty() {
    this.contextRunner.run(
        (context) -> {
          assertThat(context.getBean("otelOtlpGrpcSpanExporter", OtlpGrpcSpanExporter.class))
              .isNotNull();
        });
  }
}
