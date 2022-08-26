/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.kafka;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.KafkaTelemetry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@Configuration
@EnableConfigurationProperties(KafkaInstrumentationProperties.class)
@ConditionalOnProperty(name = "otel.springboot.kafka.enabled", matchIfMissing = true)
@ConditionalOnClass({KafkaTemplate.class, ConcurrentKafkaListenerContainerFactory.class})
public class KafkaInstrumentationAutoConfiguration {

  @Bean
  public DefaultKafkaProducerFactoryCustomizer producerInstrumentation(
      OpenTelemetry openTelemetry) {
    KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(openTelemetry);
    return producerFactory -> producerFactory.addPostProcessor(kafkaTelemetry::wrap);
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactoryPostProcessor
      concurrentKafkaListenerContainerFactoryPostProcessor(OpenTelemetry openTelemetry) {
    return new ConcurrentKafkaListenerContainerFactoryPostProcessor(openTelemetry);
  }
}
