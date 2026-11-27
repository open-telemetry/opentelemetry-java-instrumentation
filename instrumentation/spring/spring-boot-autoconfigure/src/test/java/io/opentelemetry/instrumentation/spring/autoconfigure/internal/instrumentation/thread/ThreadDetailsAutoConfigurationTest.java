/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ThreadDetailsAutoConfigurationTest {
  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withConfiguration(AutoConfigurations.of(ThreadDetailsAutoConfiguration.class));

  @Test
  void enabled() {
    runner
        .withPropertyValues("otel.spring-starter.capture-thread-attributes=true")
        .run(context -> assertThat(context).hasBean("threadDetailOtelCustomizer"));
  }

  @Test
  void disabledByDefault() {
    runner.run(context -> assertThat(context).doesNotHaveBean("threadDetailOtelCustomizer"));
  }

  @Test
  void openTelemetryDisabled() {
    runner
        .withPropertyValues(
            "otel.spring-starter.capture-thread-attributes=true", "otel.sdk.disabled=true")
        .run(context -> assertThat(context).doesNotHaveBean("threadDetailOtelCustomizer"));
  }

  @Test
  void requiresOpenTelemetryBean() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ThreadDetailsAutoConfiguration.class))
        .withPropertyValues("otel.spring-starter.capture-thread-attributes=true")
        .run(context -> assertThat(context).doesNotHaveBean("threadDetailOtelCustomizer"));
  }
}
