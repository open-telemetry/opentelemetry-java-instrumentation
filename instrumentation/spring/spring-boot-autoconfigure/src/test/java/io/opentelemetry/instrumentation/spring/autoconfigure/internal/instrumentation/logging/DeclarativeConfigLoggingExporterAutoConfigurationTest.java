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

class DeclarativeConfigLoggingExporterAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  DeclarativeConfigLoggingExporterAutoConfiguration.class,
                  OpenTelemetryAutoConfiguration.class));

  // Spring supplies every scalar as a String, so this passes only while isEnabled() reads the
  // flag through a config provider that coerces
  @Test
  void debugEnabled() {
    runner
        .withPropertyValues(
            "otel.file_format=1.1",
            "otel.instrumentation/development.java.spring_starter.debug=true")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .containsOnlyOnce("LoggingSpanExporter"));
  }

  @Test
  void debugDisabled() {
    runner
        .withPropertyValues(
            "otel.file_format=1.1",
            "otel.instrumentation/development.java.spring_starter.debug=false")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .doesNotContain("LoggingSpanExporter"));
  }

  @Test
  void debugUnset() {
    runner
        .withPropertyValues("otel.file_format=1.1")
        .run(
            context ->
                assertThat(context.getBean(OpenTelemetry.class).toString())
                    .doesNotContain("LoggingSpanExporter"));
  }

  // SpanLoggingCustomizerProviderTest instantiates the provider directly, so it passes even when
  // Spring Boot never discovers this auto-configuration
  @Test
  void registeredAsAutoConfiguration() {
    assertThat(ImportCandidates.load(AutoConfiguration.class, null))
        .contains(DeclarativeConfigLoggingExporterAutoConfiguration.class.getName());
  }

  // Spring Boot before 2.7 does not read the AutoConfiguration.imports file
  @Test
  void registeredInSpringFactories() {
    assertThat(SpringFactoriesLoader.loadFactoryNames(EnableAutoConfiguration.class, null))
        .contains(DeclarativeConfigLoggingExporterAutoConfiguration.class.getName());
  }
}
