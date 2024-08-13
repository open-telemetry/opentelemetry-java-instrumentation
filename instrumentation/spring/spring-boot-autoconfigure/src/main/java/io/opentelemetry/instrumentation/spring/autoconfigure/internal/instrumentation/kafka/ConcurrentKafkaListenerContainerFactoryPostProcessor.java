/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

class ConcurrentKafkaListenerContainerFactoryPostProcessor implements BeanPostProcessor {

  private final ObjectProvider<OpenTelemetry> openTelemetryProvider;
  private final ObjectProvider<ConfigProperties> configPropertiesProvider;

  ConcurrentKafkaListenerContainerFactoryPostProcessor(
      ObjectProvider<OpenTelemetry> openTelemetryProvider,
      ObjectProvider<ConfigProperties> configPropertiesProvider) {
    this.openTelemetryProvider = openTelemetryProvider;
    this.configPropertiesProvider = configPropertiesProvider;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!(bean instanceof ConcurrentKafkaListenerContainerFactory)) {
      return bean;
    }

    ConcurrentKafkaListenerContainerFactory<?, ?> listenerContainerFactory =
        (ConcurrentKafkaListenerContainerFactory<?, ?>) bean;
    SpringKafkaTelemetry springKafkaTelemetry =
        SpringKafkaTelemetry.builder(openTelemetryProvider.getObject())
            .setCaptureExperimentalSpanAttributes(
                configPropertiesProvider
                    .getObject()
                    .getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false))
            .build();
    listenerContainerFactory.setBatchInterceptor(springKafkaTelemetry.createBatchInterceptor());
    listenerContainerFactory.setRecordInterceptor(springKafkaTelemetry.createRecordInterceptor());

    return listenerContainerFactory;
  }
}
