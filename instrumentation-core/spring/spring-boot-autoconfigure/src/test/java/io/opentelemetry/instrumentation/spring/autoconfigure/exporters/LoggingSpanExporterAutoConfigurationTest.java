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

import io.opentelemetry.exporters.logging.LoggingSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.logging.LoggingSpanExporterAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link LoggingSpanExporter}. */
class LoggingSpanExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, LoggingSpanExporterAutoConfiguration.class));

  @Test
  @DisplayName("when exporters are ENABLED should initialize LoggingSpanExporter bean")
  void exportersEnabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporters.logging.enabled=true")
        .run(
            (context) -> {
              assertThat(context.getBean("otelLoggingSpanExporter", LoggingSpanExporter.class))
                  .isNotNull();
            });
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize LoggingSpanExporter bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporter.logging.enabled=false")
        .run(
            (context) -> {
              assertThat(context.containsBean("otelLoggingSpanExporter")).isFalse();
            });
  }

  @Test
  @DisplayName(
      "when exporter enabled property is MISSING should initialize LoggingSpanExporter bean")
  void noProperty() {
    this.contextRunner.run(
        (context) -> {
          assertThat(context.getBean("otelLoggingSpanExporter", LoggingSpanExporter.class))
              .isNotNull();
        });
  }
}
