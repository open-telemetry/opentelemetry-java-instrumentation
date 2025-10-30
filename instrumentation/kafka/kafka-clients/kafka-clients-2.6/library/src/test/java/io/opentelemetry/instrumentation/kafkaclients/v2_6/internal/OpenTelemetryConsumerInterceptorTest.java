/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
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
  void serializableConfig() throws IOException, ClassNotFoundException {
    SerializationTestUtil.testSerialize(
        consumerConfig(),
        OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_CONSUMER_TELEMETRY_SUPPLIER);
  }
}
