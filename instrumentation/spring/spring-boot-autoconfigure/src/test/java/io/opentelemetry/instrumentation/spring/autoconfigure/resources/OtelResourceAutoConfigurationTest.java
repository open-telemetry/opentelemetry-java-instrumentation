/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class OtelResourceAutoConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OtelResourceAutoConfiguration.class, OpenTelemetryAutoConfiguration.class));

  @Test
  @DisplayName(
      "when otel.springboot.resource.enabled is set to true configuration should be initialized")
  void shouldDetermineServiceNameByOtelServiceName() {
    this.contextRunner
        .withPropertyValues("otel.springboot.resource.enabled=true")
        .run(context -> assertThat(context.containsBean("otelResourceProvider")).isTrue());
  }

  @Test
  @DisplayName(
      "when otel.springboot.resource.enabled is not specified configuration should be initialized")
  void shouldInitAutoConfigurationByDefault() {
    this.contextRunner.run(
        context -> assertThat(context.containsBean("otelResourceProvider")).isTrue());
  }

  @Test
  @DisplayName(
      "when otel.springboot.resource.enabled is set to false configuration should NOT be initialized")
  void shouldNotInitAutoConfiguration() {
    this.contextRunner
        .withPropertyValues("otel.springboot.resource.enabled=false")
        .run(context -> assertThat(context.containsBean("otelResourceProvider")).isFalse());
  }
}
