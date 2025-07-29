/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnEnabledInstrumentation(module = "kafka")
@ConditionalOnClass({KafkaTemplate.class, ConcurrentKafkaListenerContainerFactory.class})
@Configuration
public class KafkaInstrumentationAutoConfiguration {

  @Bean
  DefaultKafkaProducerFactoryCustomizer otelKafkaProducerFactoryCustomizer(
      OpenTelemetry openTelemetry) {
    KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(openTelemetry);
    return producerFactory -> producerFactory.addPostProcessor(kafkaTelemetry::wrap);
  }

  @Bean
  static SpringKafkaTelemetry getTelemetry(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    return SpringKafkaTelemetry.builder(openTelemetryProvider.getObject())
        .setCaptureExperimentalSpanAttributes(
            configPropertiesProvider
                .getObject()
                .getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false))
        .build();
  }

  // static to avoid "is not eligible for getting processed by all BeanPostProcessors" warning
  @Bean
  @ConditionalOnProperty(
      name = "otel.instrumentation.kafka.autoconfigure-interceptor",
      havingValue = "true",
      matchIfMissing = true)
  static ConcurrentKafkaListenerContainerFactoryPostProcessor
      otelKafkaListenerContainerFactoryBeanPostProcessor(
          ObjectProvider<OpenTelemetry> openTelemetryProvider,
          ObjectProvider<ConfigProperties> configPropertiesProvider) {
    return new ConcurrentKafkaListenerContainerFactoryPostProcessor(
        () -> getTelemetry(openTelemetryProvider, configPropertiesProvider));
  }
}
