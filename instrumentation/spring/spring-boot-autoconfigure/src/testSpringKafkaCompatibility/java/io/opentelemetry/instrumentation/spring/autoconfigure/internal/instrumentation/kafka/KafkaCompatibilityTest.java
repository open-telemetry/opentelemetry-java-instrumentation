/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ClassUtils;

class KafkaCompatibilityTest {

  @Test
  void listenerInstrumentationRequiresSpringKafka28() {
    assertThat(
            ClassUtils.isPresent(
                "org.springframework.kafka.listener.ThreadStateProcessor",
                getClass().getClassLoader()))
        .isEqualTo(Boolean.getBoolean("springKafkaSupported"));
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(KafkaInstrumentationAutoConfiguration.class))
        .withBean(OpenTelemetry.class, OpenTelemetry::noop)
        .withBean(
            ConcurrentKafkaListenerContainerFactory.class,
            ConcurrentKafkaListenerContainerFactory::new)
        .run(context -> {
          assertThat(context).hasNotFailed();
          if (Boolean.getBoolean("springKafkaSupported")) {
            assertThat(context).hasBean("otelKafkaListenerContainerFactoryBeanPostProcessor");
            ConcurrentKafkaListenerContainerFactory<?, ?> factory =
                context.getBean(ConcurrentKafkaListenerContainerFactory.class);
            assertThat(ReflectionTestUtils.getField(factory, "batchInterceptor")).isNotNull();
            assertThat(ReflectionTestUtils.getField(factory, "recordInterceptor")).isNotNull();
          } else {
            assertThat(context)
                .doesNotHaveBean("otelKafkaListenerContainerFactoryBeanPostProcessor");
          }
        });
  }
}
