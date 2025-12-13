/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
})
@Configuration
public class KafkaInstrumentationAutoConfiguration {

  public KafkaInstrumentationAutoConfiguration() {}

  @Bean
  static SpringKafkaTelemetry getTelemetry(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    OpenTelemetry openTelemetry = openTelemetryProvider.getObject();
    return SpringKafkaTelemetry.builder(openTelemetry)
        .setCaptureExperimentalSpanAttributes(
            DeclarativeConfigUtil.getBoolean(openTelemetry, "kafka", "experimental_span_attributes")
                .orElse(false))
        .setMessagingReceiveInstrumentationEnabled(
            DeclarativeConfigUtil.getBoolean(
                    openTelemetry, "messaging", "experimental", "receive_telemetry", "enabled")
                .orElse(false))
        .setCapturedHeaders(
            DeclarativeConfigUtil.getList(
                    openTelemetry, "messaging", "experimental", "capture_headers")
                .orElse(emptyList()))
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
          ObjectProvider<OpenTelemetry> openTelemetryProvider) {
    return new ConcurrentKafkaListenerContainerFactoryPostProcessor(
        () -> getTelemetry(openTelemetryProvider));
  }
}
