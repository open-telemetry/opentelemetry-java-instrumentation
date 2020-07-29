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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import io.opentelemetry.exporters.otlp.OtlpGrpcSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpGrpcSpanExporterAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.otlp.OtlpGrpcSpanExporterProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link OtlpGrpcSpanExporterAutoConfiguration}. */
public class OtlpGrpcSpanExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, OtlpGrpcSpanExporterAutoConfiguration.class));

  @Test
  public void should_initialize_OtlpGrpcSpanExporter_bean_when_exporters_are_ENABLED() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporters.otlp.enabled=true")
        .run(
            (context) -> {
              assertNotNull(
                  "Application Context contains OtlpGrpcSpanExporter bean",
                  context.getBean("otelOtlpGrpcSpanExporter", OtlpGrpcSpanExporter.class));
            });
  }

  @Test
  public void should_initialize_OtlpGrpcSpanExporter_bean_with_property_values() {
    this.contextRunner
        .withPropertyValues(
            "opentelemetry.trace.exporter.otlp.enabled=true",
            "opentelemetry.trace.exporter.otlp.servicename=test",
            "opentelemetry.trace.exporter.otlp.endpoint=localhost:8080/test",
            "opentelemetry.trace.exporter.otlp.spantimeout=420ms")
        .run(
            (context) -> {
              OtlpGrpcSpanExporter otlpBean =
                  context.getBean("otelOtlpGrpcSpanExporter", OtlpGrpcSpanExporter.class);
              assertNotNull("Application Context contains OtlpGrpcSpanExporter bean", otlpBean);

              OtlpGrpcSpanExporterProperties otlpSpanExporterProperties =
                  context.getBean(OtlpGrpcSpanExporterProperties.class);
              assertEquals(
                  "Service Name is set in OtlpGrpcSpanExporterProperties",
                  "test",
                  otlpSpanExporterProperties.getServiceName());
              assertEquals(
                  "Endpoint is set in OtlpGrpcSpanExporterProperties",
                  "localhost:8080/test",
                  otlpSpanExporterProperties.getEndpoint());
              assertEquals(
                  "Span Timeout is set in OtlpGrpcSpanExporterProperties",
                  Duration.ofMillis(420),
                  otlpSpanExporterProperties.getSpanTimeout());
            });
  }

  @Test
  public void should_NOT_initialize_OtlpGrpcSpanExporter_bean_when_exporters_are_DISABLED() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporter.otlp.enabled=false")
        .run(
            (context) -> {
              assertFalse(
                  "Application Context DOES NOT contain otelOtlpGrpcSpanExporter bean",
                  context.containsBean("otelOtlpGrpcSpanExporter"));
            });
  }

  @Test
  public void should_initialize_OtlpGrpcSpanExporter_bean_when_otlp_enabled_property_is_MISSING() {
    this.contextRunner.run(
        (context) -> {
          assertNotNull(
              "Application Context contains otelOtlpGrpcSpanExporter bean",
              context.getBean("otelOtlpGrpcSpanExporter", OtlpGrpcSpanExporter.class));
        });
  }
}
