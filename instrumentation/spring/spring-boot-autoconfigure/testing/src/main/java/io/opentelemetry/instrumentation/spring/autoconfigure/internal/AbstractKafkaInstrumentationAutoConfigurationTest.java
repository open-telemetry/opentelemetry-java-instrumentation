/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public abstract class AbstractKafkaInstrumentationAutoConfigurationTest {
  protected abstract ApplicationContextRunner contextRunner();

  @Test
  void instrumentationDisabled() {
    contextRunner()
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
    contextRunner()
        .withPropertyValues("otel.instrumentation.kafka.autoconfigure-interceptor=false")
        .run(
            context -> {
              assertThat(context.containsBean("otelKafkaProducerFactoryCustomizer")).isTrue();
              assertThat(context.containsBean("otelKafkaListenerContainerFactoryBeanPostProcessor"))
                  .isFalse();
            });
  }
}
