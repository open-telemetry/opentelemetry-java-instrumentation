/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import com.salesforce.kafka.test.junit5.SharedKafkaTestResource;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    Set<String> expectedMetricNames =
        new HashSet<>(
            Arrays.asList(
                "kafka.consumer.commit-latency-avg",
                "kafka.consumer.commit-latency-max",
                "kafka.consumer.commit-rate",
                "kafka.consumer.commit-total",
                "kafka.consumer.failed-rebalance-rate-per-hour",
                "kafka.consumer.failed-rebalance-total",
                "kafka.consumer.heartbeat-rate",
                "kafka.consumer.heartbeat-response-time-max",
                "kafka.consumer.heartbeat-total",
                "kafka.consumer.join-rate",
                "kafka.consumer.join-time-avg",
                "kafka.consumer.join-time-max",
                "kafka.consumer.join-total",
                "kafka.consumer.last-heartbeat-seconds-ago",
                "kafka.consumer.last-rebalance-seconds-ago",
                "kafka.consumer.partition-assigned-latency-avg",
                "kafka.consumer.partition-assigned-latency-max",
                "kafka.consumer.partition-lost-latency-avg",
                "kafka.consumer.partition-lost-latency-max",
                "kafka.consumer.partition-revoked-latency-avg",
                "kafka.consumer.partition-revoked-latency-max",
                "kafka.consumer.rebalance-latency-avg",
                "kafka.consumer.rebalance-latency-max",
                "kafka.consumer.rebalance-latency-total",
                "kafka.consumer.rebalance-rate-per-hour",
                "kafka.consumer.rebalance-total",
                "kafka.consumer.sync-rate",
                "kafka.consumer.sync-time-avg",
                "kafka.consumer.sync-time-max",
                "kafka.consumer.sync-total",
                "kafka.consumer.bytes-consumed-rate",
                "kafka.consumer.bytes-consumed-total",
                "kafka.consumer.fetch-latency-avg",
                "kafka.consumer.fetch-latency-max",
                "kafka.consumer.fetch-rate",
                "kafka.consumer.fetch-size-avg",
                "kafka.consumer.fetch-size-max",
                "kafka.consumer.fetch-throttle-time-avg",
                "kafka.consumer.fetch-throttle-time-max",
                "kafka.consumer.fetch-total",
                "kafka.consumer.records-consumed-rate",
                "kafka.consumer.records-consumed-total",
                "kafka.consumer.records-lag",
                "kafka.consumer.records-lag-avg",
                "kafka.consumer.records-lag-max",
                "kafka.consumer.records-lead",
                "kafka.consumer.records-lead-avg",
                "kafka.consumer.records-lead-min",
                "kafka.consumer.records-per-request-avg",
                "kafka.consumer.connection-close-rate",
                "kafka.consumer.connection-close-total",
                "kafka.consumer.connection-count",
                "kafka.consumer.connection-creation-rate",
                "kafka.consumer.connection-creation-total",
                "kafka.consumer.failed-authentication-rate",
                "kafka.consumer.failed-authentication-total",
                "kafka.consumer.failed-reauthentication-rate",
                "kafka.consumer.failed-reauthentication-total",
                "kafka.consumer.incoming-byte-rate",
                "kafka.consumer.incoming-byte-total",
                "kafka.consumer.io-ratio",
                "kafka.consumer.io-time-ns-avg",
                "kafka.consumer.io-wait-ratio",
                "kafka.consumer.io-wait-time-ns-avg",
                "kafka.consumer.io-waittime-total",
                "kafka.consumer.iotime-total",
                "kafka.consumer.last-poll-seconds-ago",
                "kafka.consumer.network-io-rate",
                "kafka.consumer.network-io-total",
                "kafka.consumer.outgoing-byte-rate",
                "kafka.consumer.outgoing-byte-total",
                "kafka.consumer.poll-idle-ratio-avg",
                "kafka.consumer.reauthentication-latency-avg",
                "kafka.consumer.reauthentication-latency-max",
                "kafka.consumer.request-rate",
                "kafka.consumer.request-size-avg",
                "kafka.consumer.request-size-max",
                "kafka.consumer.request-total",
                "kafka.consumer.response-rate",
                "kafka.consumer.response-total",
                "kafka.consumer.select-rate",
                "kafka.consumer.select-total",
                "kafka.consumer.successful-authentication-no-reauth-total",
                "kafka.consumer.successful-authentication-rate",
                "kafka.consumer.successful-authentication-total",
                "kafka.consumer.successful-reauthentication-rate",
                "kafka.consumer.successful-reauthentication-total",
                "kafka.consumer.time-between-poll-avg",
                "kafka.consumer.time-between-poll-max",
                "kafka.consumer.incoming-byte-rate",
                "kafka.consumer.incoming-byte-total",
                "kafka.consumer.outgoing-byte-rate",
                "kafka.consumer.outgoing-byte-total",
                "kafka.consumer.request-latency-avg",
                "kafka.consumer.request-latency-max",
                "kafka.consumer.request-rate",
                "kafka.consumer.request-size-avg",
                "kafka.consumer.request-size-max",
                "kafka.consumer.request-total",
                "kafka.consumer.response-rate",
                "kafka.consumer.response-total",
                "kafka.producer.batch-size-avg",
                "kafka.producer.batch-size-max",
                "kafka.producer.batch-split-rate",
                "kafka.producer.batch-split-total",
                "kafka.producer.buffer-available-bytes",
                "kafka.producer.buffer-exhausted-rate",
                "kafka.producer.buffer-exhausted-total",
                "kafka.producer.buffer-total-bytes",
                "kafka.producer.bufferpool-wait-ratio",
                "kafka.producer.bufferpool-wait-time-total",
                "kafka.producer.compression-rate-avg",
                "kafka.producer.connection-close-rate",
                "kafka.producer.connection-close-total",
                "kafka.producer.connection-count",
                "kafka.producer.connection-creation-rate",
                "kafka.producer.connection-creation-total",
                "kafka.producer.failed-authentication-rate",
                "kafka.producer.failed-authentication-total",
                "kafka.producer.failed-reauthentication-rate",
                "kafka.producer.failed-reauthentication-total",
                "kafka.producer.incoming-byte-rate",
                "kafka.producer.incoming-byte-total",
                "kafka.producer.io-ratio",
                "kafka.producer.io-time-ns-avg",
                "kafka.producer.io-wait-ratio",
                "kafka.producer.io-wait-time-ns-avg",
                "kafka.producer.io-waittime-total",
                "kafka.producer.iotime-total",
                "kafka.producer.metadata-age",
                "kafka.producer.network-io-rate",
                "kafka.producer.network-io-total",
                "kafka.producer.outgoing-byte-rate",
                "kafka.producer.outgoing-byte-total",
                "kafka.producer.produce-throttle-time-avg",
                "kafka.producer.produce-throttle-time-max",
                "kafka.producer.reauthentication-latency-avg",
                "kafka.producer.reauthentication-latency-max",
                "kafka.producer.record-error-rate",
                "kafka.producer.record-error-total",
                "kafka.producer.record-queue-time-avg",
                "kafka.producer.record-queue-time-max",
                "kafka.producer.record-retry-rate",
                "kafka.producer.record-retry-total",
                "kafka.producer.record-send-rate",
                "kafka.producer.record-send-total",
                "kafka.producer.record-size-avg",
                "kafka.producer.record-size-max",
                "kafka.producer.records-per-request-avg",
                "kafka.producer.request-latency-avg",
                "kafka.producer.request-latency-max",
                "kafka.producer.request-rate",
                "kafka.producer.request-size-avg",
                "kafka.producer.request-size-max",
                "kafka.producer.request-total",
                "kafka.producer.requests-in-flight",
                "kafka.producer.response-rate",
                "kafka.producer.response-total",
                "kafka.producer.select-rate",
                "kafka.producer.select-total",
                "kafka.producer.successful-authentication-no-reauth-total",
                "kafka.producer.successful-authentication-rate",
                "kafka.producer.successful-authentication-total",
                "kafka.producer.successful-reauthentication-rate",
                "kafka.producer.successful-reauthentication-total",
                "kafka.producer.waiting-threads",
                "kafka.producer.incoming-byte-rate",
                "kafka.producer.incoming-byte-total",
                "kafka.producer.outgoing-byte-rate",
                "kafka.producer.outgoing-byte-total",
                "kafka.producer.request-latency-avg",
                "kafka.producer.request-latency-max",
                "kafka.producer.request-rate",
                "kafka.producer.request-size-avg",
                "kafka.producer.request-size-max",
                "kafka.producer.request-total",
                "kafka.producer.response-rate",
                "kafka.producer.response-total",
                "kafka.producer.byte-rate",
                "kafka.producer.byte-total",
                "kafka.producer.compression-rate",
                "kafka.producer.record-error-rate",
                "kafka.producer.record-error-total",
                "kafka.producer.record-retry-rate",
                "kafka.producer.record-retry-total",
                "kafka.producer.record-send-rate",
                "kafka.producer.record-send-total"));

    Set<String> metricNames = testing.metrics().stream().map(MetricData::getName).collect(toSet());

    assertThat(metricNames).containsAll(expectedMetricNames);

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
