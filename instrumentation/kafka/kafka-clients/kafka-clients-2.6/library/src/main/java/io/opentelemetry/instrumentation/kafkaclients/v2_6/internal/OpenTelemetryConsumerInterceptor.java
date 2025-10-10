/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaHelper;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * A ConsumerInterceptor that adds OpenTelemetry instrumentation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class OpenTelemetryConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

  public static final String CONFIG_KEY_KAFKA_HELPER_SUPPLIER =
      "opentelemetry.kafka-helper.supplier";

  @Nullable private KafkaHelper helper;
  private String consumerGroup;
  private String clientId;

  @Override
  @CanIgnoreReturnValue
  public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
    if (helper == null) {
      return records;
    }
    // timer should be started before fetching ConsumerRecords, but there is no callback for that
    Timer timer = Timer.start();
    Context receiveContext = helper.buildAndFinishSpan(records, consumerGroup, clientId, timer);
    if (receiveContext == null) {
      receiveContext = Context.current();
    }
    KafkaConsumerContext consumerContext =
        KafkaConsumerContextUtil.create(receiveContext, consumerGroup, clientId);
    return helper.addTracing(records, consumerContext);
  }

  @Override
  public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {}

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> configs) {
    consumerGroup = Objects.toString(configs.get(ConsumerConfig.GROUP_ID_CONFIG), null);
    clientId = Objects.toString(configs.get(ConsumerConfig.CLIENT_ID_CONFIG), null);

    KafkaHelperSupplier supplier =
        getProperty(configs, CONFIG_KEY_KAFKA_HELPER_SUPPLIER, KafkaHelperSupplier.class);
    this.helper = supplier.get();
  }

  @SuppressWarnings("unchecked")
  private static <T> T getProperty(Map<String, ?> configs, String key, Class<T> requiredType) {
    Object value = configs.get(key);
    if (value == null) {
      throw new IllegalStateException("Missing required configuration property: " + key);
    }
    if (!requiredType.isInstance(value)) {
      throw new IllegalStateException(
          "Configuration property " + key + " is not instance of " + requiredType.getSimpleName());
    }
    return (T) value;
  }
}
