/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.kafka;

import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import java.lang.reflect.Field;
import java.util.function.Supplier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.kafka.config.AbstractKafkaListenerContainerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

class ConcurrentKafkaListenerContainerFactoryPostProcessor implements BeanPostProcessor {

  private final Supplier<SpringKafkaTelemetry> springKafkaTelemetry;

  ConcurrentKafkaListenerContainerFactoryPostProcessor(
      Supplier<SpringKafkaTelemetry> springKafkaTelemetry) {
    this.springKafkaTelemetry = springKafkaTelemetry;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) {
    if (!(bean instanceof ConcurrentKafkaListenerContainerFactory)) {
      return bean;
    }

    ConcurrentKafkaListenerContainerFactory<Object, Object> listenerContainerFactory =
        (ConcurrentKafkaListenerContainerFactory<Object, Object>) bean;
    SpringKafkaTelemetry springKafkaTelemetry = this.springKafkaTelemetry.get();

    // use reflection to read existing values to avoid overwriting user configured interceptors
    BatchInterceptor<Object, Object> batchInterceptor =
        readField(listenerContainerFactory, "batchInterceptor", BatchInterceptor.class);
    RecordInterceptor<Object, Object> recordInterceptor =
        readField(listenerContainerFactory, "recordInterceptor", RecordInterceptor.class);
    listenerContainerFactory.setBatchInterceptor(
        springKafkaTelemetry.createBatchInterceptor(batchInterceptor));
    listenerContainerFactory.setRecordInterceptor(
        springKafkaTelemetry.createRecordInterceptor(recordInterceptor));

    return listenerContainerFactory;
  }

  private static <T> T readField(Object container, String filedName, Class<T> fieldType) {
    try {
      Field field = AbstractKafkaListenerContainerFactory.class.getDeclaredField(filedName);
      field.setAccessible(true);
      return fieldType.cast(field.get(container));
    } catch (Exception exception) {
      return null;
    }
  }
}
