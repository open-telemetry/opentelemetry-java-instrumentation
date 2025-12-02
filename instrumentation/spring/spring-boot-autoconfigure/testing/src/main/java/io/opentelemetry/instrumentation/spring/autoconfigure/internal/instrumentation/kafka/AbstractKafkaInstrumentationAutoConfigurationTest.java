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
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public abstract class AbstractKafkaInstrumentationAutoConfigurationTest {

  protected abstract AutoConfigurations autoConfigurations();

  protected final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withBean(
              InstrumentationConfig.class,
              () -> new ConfigPropertiesBridge(DefaultConfigProperties.createFromMap(emptyMap())))
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
}
