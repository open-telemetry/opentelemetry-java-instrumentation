/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;

@SpringBootConfiguration
@EnableAutoConfiguration
class ConsumerConfig {

  @Bean
  NewTopic batchTopic() {
    return TopicBuilder.name("testBatchTopic").partitions(1).replicas(1).build();
  }

  @Bean
  NewTopic singleTopic() {
    return TopicBuilder.name("testSingleTopic").partitions(1).replicas(1).build();
  }

  @Bean
  NewTopic nestedTopic() {
    return TopicBuilder.name("testNestedTopic").partitions(1).replicas(1).build();
  }

  @Bean
  NewTopic nestedBatchTopic() {
    return TopicBuilder.name("testNestedBatchTopic").partitions(1).replicas(1).build();
  }

  @Bean
  BatchRecordListener batchRecordListener() {
    return new BatchRecordListener();
  }

  @Bean
  SingleRecordListener singleRecordListener() {
    return new SingleRecordListener();
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> batchFactory(
      ConsumerFactory<String, String> consumerFactory,
      ObjectProvider<
              ContainerCustomizer<
                  String, String, ConcurrentMessageListenerContainer<String, String>>>
          customizerProvider) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
    factory.setAutoStartup(true);
    customizerProvider.ifAvailable(factory::setContainerCustomizer);
    return factory;
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> singleFactory(
      ConsumerFactory<String, String> consumerFactory,
      ObjectProvider<
              ContainerCustomizer<
                  String, String, ConcurrentMessageListenerContainer<String, String>>>
          customizerProvider) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(false);
    factory.setAutoStartup(true);
    try {
      // handleOne is available since Spring Kafka 2.9
      Class.forName("org.springframework.kafka.listener.CommonErrorHandler")
          .getMethod(
              "handleOne",
              Exception.class,
              ConsumerRecord.class,
              Consumer.class,
              MessageListenerContainer.class);
      ConsumerConfigUtil.addErrorHandler(factory);
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      // ignore
    }
    customizerProvider.ifAvailable(factory::setContainerCustomizer);
    return factory;
  }
}
