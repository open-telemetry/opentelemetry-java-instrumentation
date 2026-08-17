/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.testing.junit.messaging.KafkaMessagingMetricsAssertions.assertTotalConsumedMessages;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryConsumerInterceptorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static Map<String, Object> consumerConfig() {
    Map<String, Object> config = new HashMap<>();
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    config.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    config.putAll(
        KafkaTelemetry.create(testing.getOpenTelemetry()).consumerInterceptorConfigProperties());
    return config;
  }

  @Test
  void badConfig() {
    // Bad config - wrong type for supplier
    assertThatThrownBy(
            () -> {
              Map<String, Object> consumerConfig = consumerConfig();
              consumerConfig.put(
                  OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_CONSUMER_TELEMETRY_SUPPLIER,
                  "foo");
              new KafkaConsumer<>(consumerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-consumer-telemetry.supplier is not instance of KafkaConsumerTelemetrySupplier");

    // Bad config - supplier returns wrong type
    assertThatThrownBy(
            () -> {
              Map<String, Object> consumerConfig = consumerConfig();
              consumerConfig.put(
                  OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_CONSUMER_TELEMETRY_SUPPLIER,
                  (Supplier<?>) () -> "not a KafkaConsumerTelemetry");
              new KafkaConsumer<>(consumerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-consumer-telemetry.supplier is not instance of KafkaConsumerTelemetrySupplier");
  }

  @Test
  void serializableConfig() throws Exception {
    SerializationTestUtil.testSerialize(
        consumerConfig(),
        OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_CONSUMER_TELEMETRY_SUPPLIER);
  }

  @Test
  void deduplicatesRecordsAcrossReceiveInstrumentations() {
    assumeTrue(emitStableMessagingSemconv());

    KafkaTelemetry telemetry =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveTelemetryEnabled(true)
            .build();
    Map<String, ?> config = telemetry.consumerInterceptorConfigProperties();
    OpenTelemetryConsumerInterceptor<String, String> firstInterceptor =
        new OpenTelemetryConsumerInterceptor<>();
    firstInterceptor.configure(config);
    OpenTelemetryConsumerInterceptor<String, String> secondInterceptor =
        new OpenTelemetryConsumerInterceptor<>();
    secondInterceptor.configure(config);

    String topic = "test";
    TopicPartition partition = new TopicPartition(topic, 0);
    ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 0, "key", "value");
    ConsumerRecords<String, String> records =
        new ConsumerRecords<>(singletonMap(partition, singletonList(record)));

    ConsumerRecords<String, String> tracedRecords = firstInterceptor.onConsume(records);
    secondInterceptor.onConsume(tracedRecords);

    assertTotalConsumedMessages(testing, "io.opentelemetry.kafka-clients-2.6", 1);
  }

  @Test
  void disabledReceiveClearsInheritedReceiveOperation() {
    assumeTrue(emitStableMessagingSemconv());

    KafkaTelemetry telemetry =
        KafkaTelemetry.builder(testing.getOpenTelemetry())
            .setMessagingReceiveTelemetryEnabled(false)
            .build();
    KafkaConsumerTelemetrySupplier supplier =
        (KafkaConsumerTelemetrySupplier)
            requireNonNull(
                telemetry
                    .consumerInterceptorConfigProperties()
                    .get(
                        OpenTelemetryConsumerInterceptor
                            .CONFIG_KEY_KAFKA_CONSUMER_TELEMETRY_SUPPLIER));

    String topic = "test";
    TopicPartition partition = new TopicPartition(topic, 0);
    ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 0, "key", "value");
    ConsumerRecords<String, String> records =
        new ConsumerRecords<>(singletonMap(partition, singletonList(record)));

    Context receiveContext;
    Context inheritedContext =
        KafkaConsumerContextUtil.withReceiveOperation(Context.current(), true);
    try (Scope ignored = inheritedContext.makeCurrent()) {
      receiveContext =
          requireNonNull(
              supplier.get().buildAndFinishSpan(records, "test", "client", Timer.start()));
    }

    assertThat(KafkaConsumerContextUtil.hasReceiveOperation(receiveContext)).isFalse();
  }
}
