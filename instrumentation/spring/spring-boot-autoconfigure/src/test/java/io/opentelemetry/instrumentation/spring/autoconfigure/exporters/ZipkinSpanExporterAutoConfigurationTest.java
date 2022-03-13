/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporter.zipkin.ZipkinSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.zipkin.ZipkinSpanExporterAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.zipkin.ZipkinSpanExporterProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link ZipkinSpanExporter}. */
class ZipkinSpanExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, ZipkinSpanExporterAutoConfiguration.class));

  @Test
  @DisplayName("when exporters are ENABLED should initialize ZipkinSpanExporter bean")
  void exportersEnabled() {
    this.contextRunner
        .withPropertyValues("otel.exporter.zipkin.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelZipkinSpanExporter", ZipkinSpanExporter.class))
                    .isNotNull());
  }

  @Test
  @DisplayName(
      "when otel.exporter.zipkin properties are set should initialize ZipkinSpanExporterProperties with property values")
  void handlesProperties() {
    this.contextRunner
        .withPropertyValues(
            "otel.exporter.zipkin.enabled=true",
            "otel.exporter.zipkin.endpoint=http://localhost:8080/test")
        .run(
            context -> {
              ZipkinSpanExporterProperties zipkinSpanExporterProperties =
                  context.getBean(ZipkinSpanExporterProperties.class);
              assertThat(zipkinSpanExporterProperties.getEndpoint())
                  .isEqualTo("http://localhost:8080/test");
            });
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize ZipkinSpanExporter bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("otel.exporter.zipkin.enabled=false")
        .run(context -> assertThat(context.containsBean("otelZipkinSpanExporter")).isFalse());
  }

  @Test
  @DisplayName("when zipkin enabled property is MISSING should initialize ZipkinSpanExporter bean")
  void noProperty() {
    this.contextRunner.run(
        context ->
            assertThat(context.getBean("otelZipkinSpanExporter", ZipkinSpanExporter.class))
                .isNotNull());
  }
}
