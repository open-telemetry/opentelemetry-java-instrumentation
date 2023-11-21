/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import java.lang.reflect.Method;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

@SpringBootConfiguration
@EnableAutoConfiguration
public class ConsumerConfig {

  @Bean
  public NewTopic batchTopic() {
    return TopicBuilder.name("testBatchTopic").partitions(1).replicas(1).build();
  }

  @Bean
  public NewTopic singleTopic() {
    return TopicBuilder.name("testSingleTopic").partitions(1).replicas(1).build();
  }

  @Bean
  public BatchRecordListener batchRecordListener() {
    return new BatchRecordListener();
  }

  @Bean
  public SingleRecordListener singleRecordListener() {
    return new SingleRecordListener();
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(
      ConsumerFactory<String, String> consumerFactory,
      ObjectProvider<
              ContainerCustomizer<
                  String, String, ConcurrentMessageListenerContainer<String, String>>>
          customizerProvider) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    // do not retry failed records
    try {
      Class.forName("org.springframework.kafka.listener.BatchErrorHandler");
      ErrorHandlerSetter.setBatchErrorHandler(factory);
    } catch (ClassNotFoundException ignored) {
      // org.springframework.kafka.listener.BatchErrorHandler is missing in latest
      setCommonErrorHandler(factory);
    }
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
    factory.setAutoStartup(true);
    customizerProvider.ifAvailable(factory::setContainerCustomizer);
    return factory;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> singleFactory(
      ConsumerFactory<String, String> consumerFactory,
      ObjectProvider<
              ContainerCustomizer<
                  String, String, ConcurrentMessageListenerContainer<String, String>>>
          customizerProvider) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    // do not retry failed records
    try {
      Class.forName("org.springframework.kafka.listener.ErrorHandler");
      ErrorHandlerSetter.setErrorHandler(factory);
    } catch (ClassNotFoundException ignored) {
      // org.springframework.kafka.listener.ErrorHandler is missing in latest
      setCommonErrorHandler(factory);
    }
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(false);
    factory.setAutoStartup(true);
    customizerProvider.ifAvailable(factory::setContainerCustomizer);
    return factory;
  }

  private static void setCommonErrorHandler(
      ConcurrentKafkaListenerContainerFactory<String, String> factory) {
    try {
      Class<?> handlerClass =
          Class.forName("org.springframework.kafka.listener.CommonErrorHandler");
      Class<?> defaultHandlerClass =
          Class.forName("org.springframework.kafka.listener.DefaultErrorHandler");
      BackOff backOff = new FixedBackOff(0, 0);
      Object handler =
          defaultHandlerClass.getDeclaredConstructor(BackOff.class).newInstance(backOff);
      Method method = factory.getClass().getMethod("setCommonErrorHandler", handlerClass);
      method.invoke(factory, handler);
    } catch (Exception exception) {
      // ignored
    }
  }
}
