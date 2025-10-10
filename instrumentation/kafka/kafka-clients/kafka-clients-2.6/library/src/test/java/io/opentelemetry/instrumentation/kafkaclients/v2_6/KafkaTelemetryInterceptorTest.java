/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.OpenTelemetryConsumerInterceptor;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.OpenTelemetryProducerInterceptor;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class KafkaTelemetryInterceptorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static Map<String, Object> producerConfig() {
    Map<String, Object> config = new HashMap<>();
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
    config.putAll(
        KafkaTelemetry.create(testing.getOpenTelemetry()).producerInterceptorConfigProperties());
    return config;
  }

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
  void badProducerConfig() {
    Assumptions.assumeFalse(Boolean.getBoolean("testLatestDeps"));

    // Bad config - wrong type for supplier
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.put(
                  OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER, "foo");
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-telemetry.supplier is not instance of Supplier");

    // Bad config - supplier returns wrong type
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.put(
                  OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER,
                  (Supplier<?>) () -> "not a KafkaTelemetry");
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-telemetry.supplier supplier does not return KafkaTelemetry instance");
  }

  @Test
  void badConsumerConfig() {
    Assumptions.assumeFalse(Boolean.getBoolean("testLatestDeps"));

    // Bad config - wrong type for supplier
    assertThatThrownBy(
            () -> {
              Map<String, Object> consumerConfig = consumerConfig();
              consumerConfig.put(
                  OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER, "foo");
              new KafkaConsumer<>(consumerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-telemetry.supplier is not instance of Supplier");

    // Bad config - supplier returns wrong type
    assertThatThrownBy(
            () -> {
              Map<String, Object> consumerConfig = consumerConfig();
              consumerConfig.put(
                  OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER,
                  (Supplier<?>) () -> "not a KafkaTelemetry");
              new KafkaConsumer<>(consumerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-telemetry.supplier supplier does not return KafkaTelemetry instance");
  }

  @Test
  void serializableConfig() throws IOException, ClassNotFoundException {
    testSerialize(producerConfig());
    testSerialize(consumerConfig());
  }

  @SuppressWarnings("unchecked")
  private static void testSerialize(Map<String, Object> map)
      throws IOException, ClassNotFoundException {
    // Check that producer config has the supplier
    Object producerSupplier =
        map.get(OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER);
    Object consumerSupplier =
        map.get(OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER);

    KafkaTelemetrySupplier supplier = null;
    if (producerSupplier instanceof KafkaTelemetrySupplier) {
      supplier = (KafkaTelemetrySupplier) producerSupplier;
    } else if (consumerSupplier instanceof KafkaTelemetrySupplier) {
      supplier = (KafkaTelemetrySupplier) consumerSupplier;
    }

    assertThat(supplier).isNotNull();
    assertThat(supplier.get()).isNotNull();

    ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
    try (ObjectOutputStream outputStream = new ObjectOutputStream(byteOutputStream)) {
      outputStream.writeObject(map);
    }

    class CustomObjectInputStream extends ObjectInputStream {
      CustomObjectInputStream(InputStream inputStream) throws IOException {
        super(inputStream);
      }

      @Override
      protected Class<?> resolveClass(ObjectStreamClass desc)
          throws IOException, ClassNotFoundException {
        if (desc.getName().startsWith("io.opentelemetry.")) {
          throw new IllegalStateException(
              "Serial form contains opentelemetry class " + desc.getName());
        }
        return super.resolveClass(desc);
      }
    }

    try (ObjectInputStream inputStream =
        new CustomObjectInputStream(new ByteArrayInputStream(byteOutputStream.toByteArray()))) {
      Map<String, Object> result = (Map<String, Object>) inputStream.readObject();
      assertThat(result.get(OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER))
          .isNull();
      assertThat(result.get(OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_TELEMETRY_SUPPLIER))
          .isNull();
    }
  }
}
