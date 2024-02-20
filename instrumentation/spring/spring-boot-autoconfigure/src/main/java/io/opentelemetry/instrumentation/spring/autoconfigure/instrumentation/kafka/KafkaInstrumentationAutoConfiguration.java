/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.kafka;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@ConditionalOnBean(OpenTelemetry.class)
@ConditionalOnClass({KafkaTemplate.class, ConcurrentKafkaListenerContainerFactory.class})
@Conditional(KafkaInstrumentationAutoConfiguration.Condition.class)
@Configuration
public class KafkaInstrumentationAutoConfiguration {

  static final class Condition extends AllNestedConditions {
    public Condition() {
      super(ConfigurationPhase.PARSE_CONFIGURATION);
    }

    @ConditionalOnProperty(name = "otel.instrumentation.kafka.enabled", matchIfMissing = true)
    static class Kafka {}

    @ConditionalOnProperty(name = "otel.sdk.disabled", havingValue = "false", matchIfMissing = true)
    static class SdkEnabled {}
  }

  @Bean
  DefaultKafkaProducerFactoryCustomizer otelKafkaProducerFactoryCustomizer(
      OpenTelemetry openTelemetry) {
    KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(openTelemetry);
    return producerFactory -> producerFactory.addPostProcessor(kafkaTelemetry::wrap);
  }

  @Bean
  static ConcurrentKafkaListenerContainerFactoryPostProcessor
      otelKafkaListenerContainerFactoryBeanPostProcessor(
          ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new ConcurrentKafkaListenerContainerFactoryPostProcessor(openTelemetryProvider);
  }
}
