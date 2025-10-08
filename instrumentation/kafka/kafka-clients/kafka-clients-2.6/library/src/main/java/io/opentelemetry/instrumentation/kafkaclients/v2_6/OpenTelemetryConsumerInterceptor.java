/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

/**
 * A ConsumerInterceptor that adds OpenTelemetry instrumentation. Add this interceptor's class name
 * or class via ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG property to your Consumer's properties to
 * get it instantiated and used. See more details on ConsumerInterceptor usage in its Javadoc.
 *
 * <p>To configure the interceptor, use {@link KafkaTelemetry#consumerInterceptorConfigProperties}
 * to obtain the configuration properties and add them to your consumer configuration.
 *
 * @see KafkaTelemetry#consumerInterceptorConfigProperties()
 */
public class OpenTelemetryConsumerInterceptor<K, V> implements ConsumerInterceptor<K, V> {

  public static final String CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER =
      "opentelemetry.kafka-telemetry.supplier";

  @Nullable private KafkaTelemetry telemetry;
  private String consumerGroup;
  private String clientId;

  @Override
  @CanIgnoreReturnValue
  public ConsumerRecords<K, V> onConsume(ConsumerRecords<K, V> records) {
    if (telemetry == null) {
      return records;
    }
    // timer should be started before fetching ConsumerRecords, but there is no callback for that
    Timer timer = Timer.start();
    Context receiveContext = telemetry.buildAndFinishSpan(records, consumerGroup, clientId, timer);
    if (receiveContext == null) {
      receiveContext = Context.current();
    }
    KafkaConsumerContext consumerContext =
        KafkaConsumerContextUtil.create(receiveContext, consumerGroup, clientId);
    return telemetry.addTracing(records, consumerContext);
  }

  @Override
  public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {}

  @Override
  public void close() {}

  @Override
  public void configure(Map<String, ?> configs) {
    consumerGroup = Objects.toString(configs.get(ConsumerConfig.GROUP_ID_CONFIG), null);
    clientId = Objects.toString(configs.get(ConsumerConfig.CLIENT_ID_CONFIG), null);

    Object telemetrySupplier = configs.get(CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER);
    if (telemetrySupplier == null) {
      return;
    }

    if (!(telemetrySupplier instanceof Supplier)) {
      throw new IllegalStateException(
          "Configuration property "
              + CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER
              + " is not instance of Supplier");
    }

    Object kafkaTelemetry = ((Supplier<?>) telemetrySupplier).get();
    if (!(kafkaTelemetry instanceof KafkaTelemetry)) {
      throw new IllegalStateException(
          "Configuration property "
              + CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER
              + " supplier does not return KafkaTelemetry instance");
    }

    this.telemetry = (KafkaTelemetry) kafkaTelemetry;
  }
}
