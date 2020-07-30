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

import io.opentelemetry.exporters.jaeger.JaegerGrpcSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.jaeger.JaegerSpanExporterAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.jaeger.JaegerSpanExporterProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link JaegerGrpcSpanExporter}. */
class JaegerSpanExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, JaegerSpanExporterAutoConfiguration.class));

  @Test
  @DisplayName("when exporters are ENABLED should initialize JaegerGrpcSpanExporter bean")
  void shouldInitializeJaegerGrpcSpanExporterBeanWhenExportersAreEnabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporters.jaeger.enabled=true")
        .run(
            (context) -> {
              assertThat(context.getBean("otelJaegerSpanExporter", JaegerGrpcSpanExporter.class))
                  .isNotNull();
            });
  }

  @Test
  @DisplayName(
      "when opentelemetry.trace.exporter.jaeger properties are set should initialize JaegerSpanExporterProperties")
  void handlesProperties() {
    this.contextRunner
        .withPropertyValues(
            "opentelemetry.trace.exporter.jaeger.enabled=true",
            "opentelemetry.trace.exporter.jaeger.servicename=test",
            "opentelemetry.trace.exporter.jaeger.endpoint=localhost:8080/test",
            "opentelemetry.trace.exporter.jaeger.spantimeout=420ms")
        .run(
            (context) -> {
              JaegerSpanExporterProperties jaegerSpanExporterProperties =
                  context.getBean(JaegerSpanExporterProperties.class);
              assertThat(jaegerSpanExporterProperties.getServiceName()).isEqualTo("test");
              assertThat(jaegerSpanExporterProperties.getEndpoint())
                  .isEqualTo("localhost:8080/test");
              assertThat(jaegerSpanExporterProperties.getSpanTimeout())
                  .isEqualTo(Duration.ofMillis(420));
            });
  }

  @Test
  @DisplayName("when exporters are DISABLED should NOT initialize JaegerGrpcSpanExporter bean")
  void shouldNotInitializeJaegerGrpcSpanExporterBeanWhenExportersAreDisabled() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporter.jaeger.enabled=false")
        .run(
            (context) -> {
              assertThat(context.containsBean("otelJaegerSpanExporter")).isFalse();
            });
  }

  @Test
  @DisplayName(
      "when jaeger enabled property is MISSING should initialize JaegerGrpcSpanExporter bean")
  void shouldInitializeJaegerGrpcSpanExporterBeanWhenJaegerEnabledPropertyIsMissing() {
    this.contextRunner.run(
        (context) -> {
          assertThat(context.getBean("otelJaegerSpanExporter", JaegerGrpcSpanExporter.class))
              .isNotNull();
        });
  }
}
