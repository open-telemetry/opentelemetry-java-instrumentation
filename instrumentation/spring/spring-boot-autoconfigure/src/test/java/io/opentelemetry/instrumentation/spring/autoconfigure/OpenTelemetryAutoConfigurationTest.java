/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

/** Spring Boot auto configuration test for {@link OpenTelemetryAutoConfiguration}. */
class OpenTelemetryAutoConfigurationTest {
  @TestConfiguration
  static class CustomTracerConfiguration {
    @Bean
    public OpenTelemetry customOpenTelemetry() {
      return OpenTelemetry.noop();
    }
  }

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

  @AfterEach
  void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  @DisplayName(
      "when Application Context contains OpenTelemetry bean should NOT initialize openTelemetry")
  void customTracer() {
    this.contextRunner
        .withUserConfiguration(CustomTracerConfiguration.class)
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run(
            (context) -> {
              assertThat(context.containsBean("customOpenTelemetry")).isTrue();
              assertThat(context.containsBean("openTelemetry")).isFalse();
            });
  }

  @Test
  @DisplayName(
      "when Application Context DOES NOT contain OpenTelemetry bean should initialize openTelemetry")
  void initializeTracer() {
    this.contextRunner
        .withConfiguration(AutoConfigurations.of(OpenTelemetryAutoConfiguration.class))
        .run((context) -> assertThat(context.containsBean("openTelemetry")).isTrue());
  }
}
