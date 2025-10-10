/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.instrumentation.kafkaclients.v2_6.KafkaTelemetry;
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
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryProducerInterceptorTest {

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

  @Test
  void badConfig() {
    Assumptions.assumeFalse(Boolean.getBoolean("testLatestDeps"));

    // Bad config - wrong type for supplier
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.put(
                  OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_PRODUCER_TELEMETRY_SUPPLIER, "foo");
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-producer-telemetry.supplier is not instance of KafkaProducerTelemetrySupplier");

    // Bad config - supplier returns wrong type
    assertThatThrownBy(
            () -> {
              Map<String, Object> producerConfig = producerConfig();
              producerConfig.put(
                  OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_PRODUCER_TELEMETRY_SUPPLIER,
                  (Supplier<?>) () -> "not a KafkaProducerTelemetry");
              new KafkaProducer<>(producerConfig).close();
            })
        .hasRootCauseInstanceOf(IllegalStateException.class)
        .hasRootCauseMessage(
            "Configuration property opentelemetry.kafka-producer-telemetry.supplier is not instance of KafkaProducerTelemetrySupplier");
  }

  @Test
  void serializableConfig() throws IOException, ClassNotFoundException {
    testSerialize(producerConfig());
  }

  @SuppressWarnings("unchecked")
  private static void testSerialize(Map<String, Object> map)
      throws IOException, ClassNotFoundException {
    // Check that producer config has the supplier
    Object producerSupplier =
        map.get(OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_PRODUCER_TELEMETRY_SUPPLIER);

    assertThat(producerSupplier).isInstanceOf(KafkaProducerTelemetrySupplier.class);
    KafkaProducerTelemetrySupplier supplier = (KafkaProducerTelemetrySupplier) producerSupplier;
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
      assertThat(result.get(OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_PRODUCER_TELEMETRY_SUPPLIER))
          .isNull();
    }
  }
}
