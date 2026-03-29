/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public abstract class AbstractKafkaInstrumentationAutoConfigurationTest {

  protected abstract AutoConfigurations autoConfigurations();

  protected abstract void factoryTestAssertion(AssertableApplicationContext context);

  protected final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(autoConfigurations())
          .withBean("openTelemetry", OpenTelemetry.class, OpenTelemetry::noop);

  @Test
  void instrumentationDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.kafka.enabled=false")
        .run(
            context -> {
              assertThat(context.containsBean("otelKafkaProducerFactoryCustomizer")).isFalse();
              assertThat(context.containsBean("otelKafkaListenerContainerFactoryBeanPostProcessor"))
                  .isFalse();
            });
  }

  @Test
  void listenerInterceptorCanBeDisabled() {
    contextRunner
        .withPropertyValues("otel.instrumentation.kafka.autoconfigure-interceptor=false")
        .run(
            context -> {
              assertThat(context.containsBean("otelKafkaProducerFactoryCustomizer")).isTrue();
              assertThat(context.containsBean("otelKafkaListenerContainerFactoryBeanPostProcessor"))
                  .isFalse();
            });
  }

  @Test
  void defaultConfiguration() {
    contextRunner.run(
        context -> {
          assertThat(context.containsBean("otelKafkaProducerFactoryCustomizer")).isTrue();
          assertThat(context.containsBean("otelKafkaListenerContainerFactoryBeanPostProcessor"))
              .isTrue();
        });
  }

  @Test
  void defaultConfigurationWithFactoryTesting() {
    contextRunner.run(
        context -> {
          assertThat(context.containsBean("otelKafkaProducerFactoryCustomizer")).isTrue();
          assertThat(context.containsBean("otelKafkaListenerContainerFactoryBeanPostProcessor"))
              .isTrue();

          factoryTestAssertion(context);
        });
  }
}
