/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LoggingExporterAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  LoggingExporterAutoConfiguration.class, OpenTelemetryAutoConfiguration.class));

  @Test
  void debugEnabled() {
    runner
        .withPropertyValues("otel.spring-starter.debug=true", "otel.traces.exporter=none")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .containsOnlyOnce("LoggingSpanExporter"));
  }

  @Test
  void alreadyAdded() {
    runner
        .withPropertyValues("otel.spring-starter.debug=true", "otel.traces.exporter=logging")
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

  @Test
  void debugDisabled() {
    runner
        .withPropertyValues("otel.spring-starter.debug=false", "otel.traces.exporter=none")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .doesNotContain("LoggingSpanExporter"));
  }
}
