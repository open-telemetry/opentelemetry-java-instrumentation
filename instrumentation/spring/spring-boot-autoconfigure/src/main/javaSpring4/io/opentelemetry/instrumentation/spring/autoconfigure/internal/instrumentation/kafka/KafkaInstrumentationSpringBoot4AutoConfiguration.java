/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnEnabledInstrumentation(module = "kafka")
@ConditionalOnClass({
  KafkaTemplate.class,
  ConcurrentKafkaListenerContainerFactory.class,
  DefaultKafkaProducerFactoryCustomizer.class
})
@ConditionalOnMissingBean(name = "otelKafkaProducerFactoryCustomizer")
@Configuration
public class KafkaInstrumentationSpringBoot4AutoConfiguration {

  @Bean
  DefaultKafkaProducerFactoryCustomizer otelKafkaProducerFactoryCustomizer(
      OpenTelemetry openTelemetry) {
    KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(openTelemetry);
    return producerFactory -> producerFactory.addPostProcessor(kafkaTelemetry::wrap);
  }

  @Bean
  static SpringKafkaTelemetry getTelemetry(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<InstrumentationConfig> configProvider) {
    return SpringKafkaTelemetry.builder(openTelemetryProvider.getObject())
        .setCaptureExperimentalSpanAttributes(
            configProvider
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
  @ConditionalOnMissingBean
  static ConcurrentKafkaListenerContainerFactoryPostProcessor
      otelKafkaListenerContainerFactoryBeanPostProcessor(
          ObjectProvider<OpenTelemetry> openTelemetryProvider,
          ObjectProvider<InstrumentationConfig> configProvider) {
    return new ConcurrentKafkaListenerContainerFactoryPostProcessor(
        () -> getTelemetry(openTelemetryProvider, configProvider));
  }
}
