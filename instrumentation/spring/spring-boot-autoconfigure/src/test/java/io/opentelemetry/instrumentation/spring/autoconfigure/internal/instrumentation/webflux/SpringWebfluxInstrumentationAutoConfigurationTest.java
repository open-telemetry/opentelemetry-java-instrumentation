/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.webflux;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SpringWebfluxInstrumentationAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(OpenTelemetry.class, OpenTelemetry::noop)
          .withBean(
              ConfigProperties.class,
              () -> DefaultConfigProperties.createFromMap(Collections.emptyMap()))
          .withConfiguration(
              AutoConfigurations.of(SpringWebfluxInstrumentationAutoConfiguration.class));

  @Test
  void instrumentationEnabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webflux.enabled=true")
        .run(
            context ->
                assertThat(
                        context.getBean(
                            "otelWebClientBeanPostProcessor", WebClientBeanPostProcessor.class))
                    .isNotNull());
  }

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.spring-webflux.enabled=false")
        .run(
            context ->
                assertThat(context.containsBean("otelWebClientBeanPostProcessor")).isFalse());
  }

  @Test
  void defaultConfiguration() {
    contextRunner.run(
        context ->
            assertThat(
                    context.getBean(
                        "otelWebClientBeanPostProcessor", WebClientBeanPostProcessor.class))
                .isNotNull());
  }
}
