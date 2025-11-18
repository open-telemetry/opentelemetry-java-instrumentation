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

  public static final String CONFIG_KEY_KAFKA_CONSUMER_TELEMETRY_SUPPLIER =
      "opentelemetry.kafka-consumer-telemetry.supplier";

  @Nullable private KafkaConsumerTelemetry consumerTelemetry;
  private String consumerGroup;
  private String clientId;

  @Override
  @CanIgnoreReturnValue
  public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
    if (consumerTelemetry == null) {
      return records;
    }
    // timer should be started before fetching ConsumerRecords, but there is no callback for that
    Timer timer = Timer.start();
    Context receiveContext =
        consumerTelemetry.buildAndFinishSpan(records, consumerGroup, clientId, timer);
    if (receiveContext == null) {
      receiveContext = Context.current();
    }
    KafkaConsumerContext consumerContext =
        KafkaConsumerContextUtil.create(receiveContext, consumerGroup, clientId);
    return consumerTelemetry.addTracing(records, consumerContext);
  }

  @Override
  public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {}

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> configs) {
    consumerGroup = Objects.toString(configs.get(ConsumerConfig.GROUP_ID_CONFIG), null);
    clientId = Objects.toString(configs.get(ConsumerConfig.CLIENT_ID_CONFIG), null);

    KafkaConsumerTelemetrySupplier supplier =
        getProperty(
            configs,
            CONFIG_KEY_KAFKA_CONSUMER_TELEMETRY_SUPPLIER,
            KafkaConsumerTelemetrySupplier.class);
    this.consumerTelemetry = supplier.get();
  }

  private static <T> T getProperty(Map<String, ?> configs, String key, Class<T> requiredType) {
    Object value = configs.get(key);
    if (value == null) {
      throw new IllegalStateException("Missing required configuration property: " + key);
    }
    if (!requiredType.isInstance(value)) {
      throw new IllegalStateException(
          "Configuration property " + key + " is not instance of " + requiredType.getSimpleName());
    }
    return requiredType.cast(value);
  }
}
