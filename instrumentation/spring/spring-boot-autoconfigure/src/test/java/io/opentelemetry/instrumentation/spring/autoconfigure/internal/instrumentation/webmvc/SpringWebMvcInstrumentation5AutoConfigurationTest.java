/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webmvc;

import static io.opentelemetry.instrumentation.spring.autoconfigure.internal.AbstractKafkaInstrumentationAutoConfigurationTest.EMPTY_INSTRUMENTATION_CONFIG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import javax.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpringWebMvcInstrumentation5AutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withBean(InstrumentationConfig.class, () -> EMPTY_INSTRUMENTATION_CONFIG)
          .withConfiguration(
              AutoConfigurations.of(SpringWebMvc5InstrumentationAutoConfiguration.class));

  @BeforeEach
  void setUp() {
    assumeFalse(Boolean.getBoolean("testLatestDeps"));
  }

  @Test
  void instrumentationEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webmvc.enabled=true")
        .run(context -> assertThat(context.getBean("otelWebMvcFilter", Filter.class)).isNotNull());
  }

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webmvc.enabled=false")
        .run(context -> assertThat(context.containsBean("otelWebMvcFilter")).isFalse());
  }

  @Test
  void defaultConfiguration() {
    contextRunner.run(
        context -> assertThat(context.getBean("otelWebMvcFilter", Filter.class)).isNotNull());
  }
}
