/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.webmvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link WebMvcFilterAutoConfigurationSpring6}. */
class WebMvcFilterAutoConfigurationSpring6Test {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, WebMvcFilterAutoConfigurationSpring6.class));

  @BeforeAll
  static void setUp() {
    assumeTrue(Boolean.getBoolean("testLatestDeps"));
  }

  @Test
  @DisplayName("when web is ENABLED should initialize WebMvcTracingFilter bean")
  void webEnabled() {
    this.contextRunner
        .withPropertyValues("otel.springboot.web.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelWebMvcInstrumentationFilter", Filter.class))
                    .isNotNull());
  }

  @Test
  @DisplayName("when web is DISABLED should NOT initialize WebMvcTracingFilter bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("otel.springboot.web.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("otelWebMvcInstrumentationFilter")).isFalse());
  }

  @Test
  @DisplayName("when web property is MISSING should initialize WebMvcTracingFilter bean")
  void noProperty() {
    this.contextRunner.run(
        context ->
            assertThat(context.getBean("otelWebMvcInstrumentationFilter", Filter.class))
                .isNotNull());
  }
}
