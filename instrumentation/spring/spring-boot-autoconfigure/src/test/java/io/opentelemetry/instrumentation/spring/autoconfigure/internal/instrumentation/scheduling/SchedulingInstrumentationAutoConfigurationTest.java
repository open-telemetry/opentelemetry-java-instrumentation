/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SchedulingInstrumentationAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(
              AutoConfigurations.of(SpringSchedulingInstrumentationAutoConfiguration.class));

  @Test
  void instrumentationEnabled() {
    runner
        .withPropertyValues("otel.instrumentation.spring-scheduling.enabled=true")
        .run(
            context ->
                assertThat(context.containsBean("springSchedulingInstrumentationAspect")).isTrue());
  }

  @Test
  void instrumentationDisabled() {
    runner
        .withPropertyValues("otel.instrumentation.spring-scheduling.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("springSchedulingInstrumentationAspect"))
                    .isFalse());
  }
}
