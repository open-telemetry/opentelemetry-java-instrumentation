/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.annotations;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class InstrumentationAnnotationsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(
              AutoConfigurations.of(InstrumentationAnnotationsAutoConfiguration.class));

  @Test
  void instrumentationEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.annotations.enabled=true")
        .run(context -> assertThat(context).hasBean("otelInstrumentationWithSpanAspect"));
  }

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.annotations.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean("otelInstrumentationWithSpanAspect"));
  }

  @Test
  void defaultConfiguration() {
    contextRunner.run(context -> assertThat(context).hasBean("otelInstrumentationWithSpanAspect"));
  }
}
