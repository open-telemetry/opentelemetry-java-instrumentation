/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.testing;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;

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
      ConsumerFactory<String, String> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    // do not retry failed records
    factory.setBatchErrorHandler(new DoNothingBatchErrorHandler());
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(true);
    factory.setAutoStartup(true);
    return factory;
  }

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, String> singleFactory(
      ConsumerFactory<String, String> consumerFactory) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    // do not retry failed records
    factory.setErrorHandler(new DoNothingErrorHandler());
    factory.setConsumerFactory(consumerFactory);
    factory.setBatchListener(false);
    factory.setAutoStartup(true);
    return factory;
  }
}
