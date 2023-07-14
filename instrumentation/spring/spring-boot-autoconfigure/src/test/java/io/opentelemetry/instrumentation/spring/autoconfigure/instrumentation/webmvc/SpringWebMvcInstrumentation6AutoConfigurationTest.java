/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.webmvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.OpenTelemetry;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpringWebMvcInstrumentation6AutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(
              AutoConfigurations.of(SpringWebMvc6InstrumentationAutoConfiguration.class));

  @BeforeEach
  void setUp() {
    assumeTrue(Boolean.getBoolean("testLatestDeps"));
  }

  @Test
  void instrumentationEnabled() {
    this.contextRunner
        .withPropertyValues("otel.instrumentation.spring-webmvc.enabled=true")
        .run(context -> assertThat(context.getBean("otelWebMvcFilter", Filter.class)).isNotNull());
  }

  @Test
  void instrumentationDisabled() {
    this.contextRunner
        .withPropertyValues("otel.instrumentation.spring-webmvc.enabled=false")
        .run(context -> assertThat(context.containsBean("otelWebMvcFilter")).isFalse());
  }

  @Test
  void defaultConfiguration() {
    this.contextRunner.run(
        context -> assertThat(context.getBean("otelWebMvcFilter", Filter.class)).isNotNull());
  }
}
