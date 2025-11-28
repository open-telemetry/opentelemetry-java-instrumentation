/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties.ConfigPropertiesBridge;
import io.opentelemetry.sdk.autoconfigure.spi.internal.DefaultConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;

class KafkaInstrumentationAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withBean(
              InstrumentationConfig.class,
              () -> new ConfigPropertiesBridge(DefaultConfigProperties.createFromMap(emptyMap())))
          .withConfiguration(
              AutoConfigurations.of(KafkaInstrumentationSpringBoot4AutoConfiguration.class))
          .withBean("openTelemetry", OpenTelemetry.class, OpenTelemetry::noop);

  @Test
  void defaultConfiguration() {
    runner.run(
        context -> {
          assertThat(context.containsBean("otelKafkaProducerFactoryCustomizer")).isTrue();
          assertThat(context.containsBean("otelKafkaListenerContainerFactoryBeanPostProcessor"))
              .isTrue();

          DefaultKafkaProducerFactoryCustomizer customizer =
              context.getBean(
                  "otelKafkaProducerFactoryCustomizer",
                  DefaultKafkaProducerFactoryCustomizer.class);
          assertThat(customizer).isNotNull();

          // Verify the customizer works by applying it to a producer factory
          DefaultKafkaProducerFactory<Object, Object> factory =
              new DefaultKafkaProducerFactory<>(emptyMap());
          customizer.customize(factory);

          // Check that interceptors were added (the customizer adds a post processor)
          assertThat(factory.getPostProcessors()).isNotEmpty();
        });
  }

  @Test
  void instrumentationDisabled() {
    runner
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
    runner
        .withPropertyValues("otel.instrumentation.kafka.autoconfigure-interceptor=false")
        .run(
            context -> {
              assertThat(context.containsBean("otelKafkaProducerFactoryCustomizer")).isTrue();
              assertThat(context.containsBean("otelKafkaListenerContainerFactoryBeanPostProcessor"))
                  .isFalse();
            });
  }
}
