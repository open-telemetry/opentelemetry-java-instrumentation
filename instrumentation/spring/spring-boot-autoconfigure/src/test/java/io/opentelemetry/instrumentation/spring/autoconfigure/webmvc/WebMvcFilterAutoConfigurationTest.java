/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.webmvc;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import javax.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link WebMvcFilterAutoConfiguration}. */
class WebMvcFilterAutoConfigurationTest {
  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, WebMvcFilterAutoConfiguration.class));

  @AfterEach
  void tearDown() {
    GlobalOpenTelemetry.resetForTest();
  }

  @Test
  @DisplayName("when web is ENABLED should initialize WebMvcTracingFilter bean")
  void webEnabled() {
    this.contextRunner
        .withPropertyValues("otel.springboot.web.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("otelWebMvcTracingFilter", Filter.class)).isNotNull());
  }

  @Test
  @DisplayName("when web is DISABLED should NOT initialize WebMvcTracingFilter bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("otel.springboot.web.enabled=false")
        .run(context -> assertThat(context.containsBean("otelWebMvcTracingFilter")).isFalse());
  }

  @Test
  @DisplayName("when web property is MISSING should initialize WebMvcTracingFilter bean")
  void noProperty() {
    this.contextRunner.run(
        context ->
            assertThat(context.getBean("otelWebMvcTracingFilter", Filter.class)).isNotNull());
  }
}
