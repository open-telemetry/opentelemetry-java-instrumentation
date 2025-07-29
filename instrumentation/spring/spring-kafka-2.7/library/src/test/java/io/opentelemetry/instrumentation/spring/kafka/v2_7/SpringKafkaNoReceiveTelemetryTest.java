/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.testing.AbstractSpringKafkaNoReceiveTelemetryTest;
import java.util.List;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;

class SpringKafkaNoReceiveTelemetryTest extends AbstractSpringKafkaNoReceiveTelemetryTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected List<Class<?>> additionalSpringConfigs() {
    return singletonList(KafkaInstrumentationConfig.class);
  }

  @Override
  protected boolean isLibraryInstrumentationTest() {
    return true;
  }

  @Configuration
  public static class KafkaInstrumentationConfig {

    @Bean
    public DefaultKafkaProducerFactoryCustomizer producerInstrumentation() {
      KafkaTelemetry kafkaTelemetry = KafkaTelemetry.create(testing.getOpenTelemetry());
      return producerFactory -> producerFactory.addPostProcessor(kafkaTelemetry::wrap);
    }

    @Bean
    public ContainerCustomizer<String, String, ConcurrentMessageListenerContainer<String, String>>
        listenerCustomizer() {
      SpringKafkaTelemetry springKafkaTelemetry =
          SpringKafkaTelemetry.create(testing.getOpenTelemetry());
      return container -> {
        container.setRecordInterceptor(springKafkaTelemetry.createRecordInterceptor());
        container.setBatchInterceptor(springKafkaTelemetry.createBatchInterceptor());
      };
    }
  }
}
