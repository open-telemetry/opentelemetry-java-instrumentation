/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class OpenTelemetryKafkaMetricsTest {

  private static final List<String> TOPICS = Arrays.asList("foo", "bar", "baz", "qux");
  private static final Random RANDOM = new Random();

  @RegisterExtension static final SharedKafkaTestResource kafka = new SharedKafkaTestResource();

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Test
  void observeMetrics() {
    OpenTelemetryKafkaMetrics.setOpenTelemetry(testing.getOpenTelemetry());

    produceRecords();
    consumeRecords();

    testing.waitAndAssertMetrics(
        "io.opentelemetry.kafka-clients",
        "messaging.kafka.producer.outgoing-bytes.rate",
        unused -> {});
    testing.waitAndAssertMetrics(
        "io.opentelemetry.kafka-clients", "messaging.kafka.producer.responses.rate", unused -> {});
    testing.waitAndAssertMetrics(
        "io.opentelemetry.kafka-clients", "messaging.kafka.producer.bytes.rate", unused -> {});
    testing.waitAndAssertMetrics(
        "io.opentelemetry.kafka-clients",
        "messaging.kafka.producer.compression-ratio",
        unused -> {});
    testing.waitAndAssertMetrics(
        "io.opentelemetry.kafka-clients",
        "messaging.kafka.producer.record-error.rate",
        unused -> {});
    testing.waitAndAssertMetrics(
        "io.opentelemetry.kafka-clients",
        "messaging.kafka.producer.record-retry.rate",
        unused -> {});
    testing.waitAndAssertMetrics(
        "io.opentelemetry.kafka-clients",
        "messaging.kafka.producer.record-sent.rate",
        unused -> {});
    testing.waitAndAssertMetrics(
        "io.opentelemetry.kafka-clients", "messaging.kafka.consumer.lag", unused -> {});

    // Print mapping table
    OpenTelemetryKafkaMetrics.printMappingTable();
  }

  void produceRecords() {
    Map<String, Object> config = new HashMap<>();
    // Register OpenTelemetryKafkaMetrics as reporter
    config.put(
        ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, OpenTelemetryKafkaMetrics.class.getName());
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getKafkaConnectString());
    config.put(ProducerConfig.CLIENT_ID_CONFIG, "sample-client-id");
    config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
    config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "gzip");

    try (KafkaProducer<byte[], byte[]> producer = new KafkaProducer<>(config)) {
      for (int i = 0; i < 100; i++) {
        producer.send(
            new ProducerRecord<>(
                TOPICS.get(RANDOM.nextInt(TOPICS.size())),
                0,
                System.currentTimeMillis(),
                "key".getBytes(StandardCharsets.UTF_8),
                "value".getBytes(StandardCharsets.UTF_8)));
      }
    }
  }

  void consumeRecords() {
    Map<String, Object> config = new HashMap<>();
    // Register OpenTelemetryKafkaMetrics as reporter
    config.put(
        ConsumerConfig.METRIC_REPORTER_CLASSES_CONFIG, OpenTelemetryKafkaMetrics.class.getName());
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getKafkaConnectString());
    config.put(ConsumerConfig.GROUP_ID_CONFIG, "sample-group");
    config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    config.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
    config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 2);

    try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(config)) {
      consumer.subscribe(TOPICS);
      Instant stopTime = Instant.now().plusSeconds(10);
      while (Instant.now().isBefore(stopTime)) {
        consumer.poll(1000);
      }
    }
  }
}
