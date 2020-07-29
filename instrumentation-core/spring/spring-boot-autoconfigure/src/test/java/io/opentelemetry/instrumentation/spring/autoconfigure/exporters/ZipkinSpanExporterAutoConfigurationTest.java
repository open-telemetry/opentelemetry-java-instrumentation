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

import io.opentelemetry.exporters.zipkin.ZipkinSpanExporter;
import io.opentelemetry.instrumentation.spring.autoconfigure.TracerAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.zipkin.ZipkinSpanExporterAutoConfiguration;
import io.opentelemetry.instrumentation.spring.autoconfigure.exporters.zipkin.ZipkinSpanExporterProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link ZipkinSpanExporter}. */
public class ZipkinSpanExporterAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  TracerAutoConfiguration.class, ZipkinSpanExporterAutoConfiguration.class));

  @Test
  public void should_initialize_ZipkinSpanExporter_bean_when_exporters_are_ENABLED() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporters.zipkin.enabled=true")
        .run(
            (context) -> {
              assertNotNull(
                  "Application Context contains ZipkinSpanExporter bean",
                  context.getBean("otelZipkinSpanExporter", ZipkinSpanExporter.class));
            });
  }

  @Test
  public void should_initialize_ZipkinSpanExporter_bean_with_property_values() {
    this.contextRunner
        .withPropertyValues(
            "opentelemetry.trace.exporter.zipkin.enabled=true",
            "opentelemetry.trace.exporter.zipkin.servicename=test",
            "opentelemetry.trace.exporter.zipkin.endpoint=http://localhost:8080/test")
        .run(
            (context) -> {
              ZipkinSpanExporter zipkinBean =
                  context.getBean("otelZipkinSpanExporter", ZipkinSpanExporter.class);
              assertNotNull("Application Context contains ZipkinSpanExporter bean", zipkinBean);

              ZipkinSpanExporterProperties zipkinSpanExporterProperties =
                  context.getBean(ZipkinSpanExporterProperties.class);
              assertEquals(
                  "Service Name is set in ZipkinSpanExporterProperties",
                  "test",
                  zipkinSpanExporterProperties.getServiceName());
              assertEquals(
                  "Endpoint is set in ZipkinSpanExporterProperties",
                  "http://localhost:8080/test",
                  zipkinSpanExporterProperties.getEndpoint());
            });
  }

  @Test
  public void should_NOT_initialize_ZipkinSpanExporter_bean_when_exporters_are_DISABLED() {
    this.contextRunner
        .withPropertyValues("opentelemetry.trace.exporter.zipkin.enabled=false")
        .run(
            (context) -> {
              assertFalse(
                  "Application Context DOES NOT contain otelZipkinSpanExporter bean",
                  context.containsBean("otelZipkinSpanExporter"));
            });
  }

  @Test
  public void should_initialize_ZipkinSpanExporter_bean_when_zipkin_enabled_property_is_MISSING() {
    this.contextRunner.run(
        (context) -> {
          assertNotNull(
              "Application Context contains otelZipkinSpanExporter bean",
              context.getBean("otelZipkinSpanExporter", ZipkinSpanExporter.class));
        });
  }
}
