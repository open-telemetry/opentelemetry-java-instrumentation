/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.instrumentation.kafka;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

class ConcurrentKafkaListenerContainerFactoryPostProcessor implements BeanPostProcessor {

  private final OpenTelemetry openTelemetry;

  ConcurrentKafkaListenerContainerFactoryPostProcessor(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!(bean instanceof ConcurrentKafkaListenerContainerFactory)) {
      return bean;
    }

    ConcurrentKafkaListenerContainerFactory<?, ?> listenerContainerFactory =
        (ConcurrentKafkaListenerContainerFactory<?, ?>) bean;
    SpringKafkaTelemetry springKafkaTelemetry = SpringKafkaTelemetry.create(openTelemetry);
    listenerContainerFactory.setBatchInterceptor(springKafkaTelemetry.createBatchInterceptor());
    listenerContainerFactory.setRecordInterceptor(springKafkaTelemetry.createRecordInterceptor());

    return listenerContainerFactory;
  }
}
