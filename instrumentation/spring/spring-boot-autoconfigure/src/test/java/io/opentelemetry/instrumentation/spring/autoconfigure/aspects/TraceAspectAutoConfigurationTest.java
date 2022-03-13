/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.aspects;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.spring.autoconfigure.OpenTelemetryAutoConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Spring Boot auto configuration test for {@link TraceAspectAutoConfiguration}. */
public class TraceAspectAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  OpenTelemetryAutoConfiguration.class, TraceAspectAutoConfiguration.class));

  @Test
  @DisplayName("when aspects are ENABLED should initialize WithSpanAspect bean")
  void aspectsEnabled() {
    this.contextRunner
        .withPropertyValues("otel.springboot.aspects.enabled=true")
        .run(
            context ->
                assertThat(context.getBean("withSpanAspect", WithSpanAspect.class)).isNotNull());
  }

  @Test
  @DisplayName("when aspects are DISABLED should NOT initialize WithSpanAspect bean")
  void disabledProperty() {
    this.contextRunner
        .withPropertyValues("otel.springboot.aspects.enabled=false")
        .run(context -> assertThat(context.containsBean("withSpanAspect")).isFalse());
  }

  @Test
  @DisplayName("when aspects enabled property is MISSING should initialize WithSpanAspect bean")
  void noProperty() {
    this.contextRunner.run(
        context -> assertThat(context.getBean("withSpanAspect", WithSpanAspect.class)).isNotNull());
  }
}
