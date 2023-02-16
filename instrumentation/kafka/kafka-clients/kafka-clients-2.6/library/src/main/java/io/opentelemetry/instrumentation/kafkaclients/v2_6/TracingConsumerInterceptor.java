/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * A ConsumerInterceptor that adds tracing capability. Add this interceptor's class name or class
 * via ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG property to your Consumer's properties to get it
 * instantiated and used. See more details on ConsumerInterceptor usage in its Javadoc.
 */
public class TracingConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

  private static final KafkaTelemetry telemetry = KafkaTelemetry.create(GlobalOpenTelemetry.get());

  private String clientId;
  private String consumerGroup;

  @Override
  public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
    telemetry.buildAndFinishSpan(records, consumerGroup, clientId);
    return records;
  }

  @Override
  public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {}

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> configs) {
    consumerGroup = Objects.toString(configs.get(ConsumerConfig.GROUP_ID_CONFIG));
    clientId = Objects.toString(configs.get(ConsumerConfig.CLIENT_ID_CONFIG));

    // TODO: support experimental attributes config
  }
}
