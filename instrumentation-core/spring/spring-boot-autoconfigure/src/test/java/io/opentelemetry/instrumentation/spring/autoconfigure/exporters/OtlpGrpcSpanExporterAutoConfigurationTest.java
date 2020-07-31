/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.exporters;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpGrpcSpanExporterAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpGrpcSpanExporterProperties;
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

  @Test
  @DisplayName(
      "when opentelemetry.trace.exporter.otlp properties are set should initialize OtlpGrpcSpanExporterProperties")
  void handlesProperties() {
    this.contextRunner
        .withPropertyValues(
            "opentelemetry.trace.exporter.otlp.enabled=true",
            "opentelemetry.trace.exporter.otlp.servicename=test",
            "opentelemetry.trace.exporter.otlp.endpoint=localhost:8080/test",
            "opentelemetry.trace.exporter.otlp.spantimeout=69ms")
        .run(
            (context) -> {
              OtlpGrpcSpanExporterProperties otlpSpanExporterProperties =
                  context.getBean(OtlpGrpcSpanExporterProperties.class);
              assertThat(otlpSpanExporterProperties.getServiceName()).isEqualTo("test");
              assertThat(otlpSpanExporterProperties.getEndpoint()).isEqualTo("localhost:8080/test");
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
