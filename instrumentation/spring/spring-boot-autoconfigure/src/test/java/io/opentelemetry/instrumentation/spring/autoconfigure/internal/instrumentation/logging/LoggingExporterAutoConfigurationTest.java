/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.logging;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.support.SpringFactoriesLoader;

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

  // the tests above register this class explicitly, so they pass even when Spring Boot never
  // discovers it
  @Test
  void registeredAsAutoConfiguration() {
    assertThat(ImportCandidates.load(AutoConfiguration.class, null))
        .contains(LoggingExporterAutoConfiguration.class.getName());
  }

  // Spring Boot before 2.7 does not read the AutoConfiguration.imports file
  @Test
  void registeredInSpringFactories() {
    assertThat(SpringFactoriesLoader.loadFactoryNames(EnableAutoConfiguration.class, null))
        .contains(LoggingExporterAutoConfiguration.class.getName());
  }
}
