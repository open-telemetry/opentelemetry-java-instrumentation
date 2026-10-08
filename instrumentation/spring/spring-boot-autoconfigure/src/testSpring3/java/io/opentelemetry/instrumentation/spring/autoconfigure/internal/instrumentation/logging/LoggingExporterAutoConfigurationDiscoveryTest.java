/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Unlike {@code LoggingExporterAutoConfigurationTest}, this goes through the real
 * auto-configuration import so that a missing registration entry is caught.
 */
class LoggingExporterAutoConfigurationDiscoveryTest {

  @Configuration
  @EnableAutoConfiguration
  static class ScanConfiguration {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(ScanConfiguration.class)
          // the otlp exporter is not on the classpath of this test source set
          .withPropertyValues(
              "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none");

  @Test
  void debugEnabled() {
    runner
        .withPropertyValues("otel.spring-starter.debug=true")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .containsOnlyOnce("LoggingSpanExporter"));
  }

  @Test
  void debugUnset() {
    runner.run(
        context ->
            assertThat(context.getBean(OpenTelemetry.class).toString())
                .doesNotContain("LoggingSpanExporter"));
  }
}
