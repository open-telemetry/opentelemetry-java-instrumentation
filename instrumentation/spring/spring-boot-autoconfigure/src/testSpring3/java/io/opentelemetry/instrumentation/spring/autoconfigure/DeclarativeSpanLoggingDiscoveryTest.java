/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class DeclarativeSpanLoggingDiscoveryTest {
  @Configuration
  @EnableAutoConfiguration
  static class ScanConfiguration {}

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(ScanConfiguration.class)
          .withPropertyValues("otel.file_format=1.1");

  @Test
  void debugEnabled() {
    // Import through Spring Boot, not by explicitly registering the logging configuration.
    runner
        .withPropertyValues("otel.instrumentation/development.java.spring_starter.debug=true")
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
