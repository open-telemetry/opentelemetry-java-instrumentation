/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.kafka.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertTotalConsumedMessages;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.OpenTelemetryConsumerInterceptor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.kafka.listener.BatchInterceptor;
import org.springframework.kafka.listener.RecordInterceptor;

class SpringKafkaInterceptorDeliveryTest {

  private static final String KAFKA_INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-2.6";
  private static final String TOPIC = "test";
  private static final TopicPartition PARTITION = new TopicPartition(TOPIC, 0);

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void batchRetriesUseInterceptorDeliveryTracker() {
    assumeTrue(emitStableMessagingSemconv());
    OpenTelemetryConsumerInterceptor<String, String> consumerInterceptor = consumerInterceptor();
    BatchInterceptor<String, String> springInterceptor =
        SpringKafkaTelemetry.create(testing.getOpenTelemetry()).createBatchInterceptor();
    Consumer<String, String> consumer = consumer();

    for (int attempt = 0; attempt < 3; attempt++) {
      ConsumerRecords<String, String> records = consumerInterceptor.onConsume(records());
      springInterceptor.intercept(records, consumer);
      if (attempt < 2) {
        springInterceptor.failure(records, failure(), consumer);
      } else {
        springInterceptor.success(records, consumer);
      }
    }

    assertTotalConsumedMessages(testing, KAFKA_INSTRUMENTATION_NAME, 1);
  }

  @Test
  void recordRetriesUseInterceptorDeliveryTracker() {
    assumeTrue(emitStableMessagingSemconv());
    OpenTelemetryConsumerInterceptor<String, String> consumerInterceptor = consumerInterceptor();
    RecordInterceptor<String, String> springInterceptor =
        SpringKafkaTelemetry.create(testing.getOpenTelemetry()).createRecordInterceptor();
    Consumer<String, String> consumer = consumer();

    for (int attempt = 0; attempt < 3; attempt++) {
      ConsumerRecord<String, String> record =
          consumerInterceptor.onConsume(records()).records(PARTITION).get(0);
      springInterceptor.intercept(record, consumer);
      if (attempt < 2) {
        springInterceptor.failure(record, failure(), consumer);
      } else {
        springInterceptor.success(record, consumer);
      }
    }

    assertTotalConsumedMessages(testing, KAFKA_INSTRUMENTATION_NAME, 1);
  }

  private static OpenTelemetryConsumerInterceptor<String, String> consumerInterceptor() {
    KafkaTelemetry kafkaTelemetry =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveTelemetryEnabled(true)
            .build();
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.GROUP_ID_CONFIG, "group");
    config.put(ConsumerConfig.CLIENT_ID_CONFIG, "client");
    config.putAll(kafkaTelemetry.consumerInterceptorConfigProperties());
    OpenTelemetryConsumerInterceptor<String, String> interceptor =
        new OpenTelemetryConsumerInterceptor<>();
    interceptor.configure(config);
    return interceptor;
  }

  @SuppressWarnings("unchecked")
  private static Consumer<String, String> consumer() {
    return mock(Consumer.class);
  }

  private static IllegalArgumentException failure() {
    return new IllegalArgumentException("failure");
  }

  private static ConsumerRecords<String, String> records() {
    ConsumerRecord<String, String> record =
        new ConsumerRecord<>(TOPIC, PARTITION.partition(), 1, "key", "value");
    return new ConsumerRecords<>(singletonMap(PARTITION, singletonList(record)));
  }
}
