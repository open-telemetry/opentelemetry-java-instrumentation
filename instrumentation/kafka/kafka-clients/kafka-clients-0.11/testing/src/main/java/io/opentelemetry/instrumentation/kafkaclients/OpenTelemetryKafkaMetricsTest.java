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
                "consumer-coordinator-metrics.assigned-partitions",
                "consumer-coordinator-metrics.commit-latency-avg",
                "consumer-coordinator-metrics.commit-latency-max",
                "consumer-coordinator-metrics.commit-rate",
                "consumer-coordinator-metrics.commit-total",
                "consumer-coordinator-metrics.failed-rebalance-rate-per-hour",
                "consumer-coordinator-metrics.failed-rebalance-total",
                "consumer-coordinator-metrics.heartbeat-rate",
                "consumer-coordinator-metrics.heartbeat-response-time-max",
                "consumer-coordinator-metrics.heartbeat-total",
                "consumer-coordinator-metrics.join-rate",
                "consumer-coordinator-metrics.join-time-avg",
                "consumer-coordinator-metrics.join-time-max",
                "consumer-coordinator-metrics.join-total",
                "consumer-coordinator-metrics.last-heartbeat-seconds-ago",
                "consumer-coordinator-metrics.last-rebalance-seconds-ago",
                "consumer-coordinator-metrics.partition-assigned-latency-avg",
                "consumer-coordinator-metrics.partition-assigned-latency-max",
                "consumer-coordinator-metrics.partition-lost-latency-avg",
                "consumer-coordinator-metrics.partition-lost-latency-max",
                "consumer-coordinator-metrics.partition-revoked-latency-avg",
                "consumer-coordinator-metrics.partition-revoked-latency-max",
                "consumer-coordinator-metrics.rebalance-latency-avg",
                "consumer-coordinator-metrics.rebalance-latency-max",
                "consumer-coordinator-metrics.rebalance-latency-total",
                "consumer-coordinator-metrics.rebalance-rate-per-hour",
                "consumer-coordinator-metrics.rebalance-total",
                "consumer-coordinator-metrics.sync-rate",
                "consumer-coordinator-metrics.sync-time-avg",
                "consumer-coordinator-metrics.sync-time-max",
                "consumer-coordinator-metrics.sync-total",
                "consumer-fetch-manager-metrics.bytes-consumed-rate",
                "consumer-fetch-manager-metrics.bytes-consumed-total",
                "consumer-fetch-manager-metrics.fetch-latency-avg",
                "consumer-fetch-manager-metrics.fetch-latency-max",
                "consumer-fetch-manager-metrics.fetch-rate",
                "consumer-fetch-manager-metrics.fetch-size-avg",
                "consumer-fetch-manager-metrics.fetch-size-max",
                "consumer-fetch-manager-metrics.fetch-throttle-time-avg",
                "consumer-fetch-manager-metrics.fetch-throttle-time-max",
                "consumer-fetch-manager-metrics.fetch-total",
                "consumer-fetch-manager-metrics.records-consumed-rate",
                "consumer-fetch-manager-metrics.records-consumed-total",
                "consumer-fetch-manager-metrics.records-lag",
                "consumer-fetch-manager-metrics.records-lag-avg",
                "consumer-fetch-manager-metrics.records-lag-max",
                "consumer-fetch-manager-metrics.records-lead",
                "consumer-fetch-manager-metrics.records-lead-avg",
                "consumer-fetch-manager-metrics.records-lead-min",
                "consumer-fetch-manager-metrics.records-per-request-avg",
                "consumer-metrics.connection-close-rate",
                "consumer-metrics.connection-close-total",
                "consumer-metrics.connection-count",
                "consumer-metrics.connection-creation-rate",
                "consumer-metrics.connection-creation-total",
                "consumer-metrics.failed-authentication-rate",
                "consumer-metrics.failed-authentication-total",
                "consumer-metrics.failed-reauthentication-rate",
                "consumer-metrics.failed-reauthentication-total",
                "consumer-metrics.incoming-byte-rate",
                "consumer-metrics.incoming-byte-total",
                "consumer-metrics.io-ratio",
                "consumer-metrics.io-time-ns-avg",
                "consumer-metrics.io-wait-ratio",
                "consumer-metrics.io-wait-time-ns-avg",
                "consumer-metrics.io-waittime-total",
                "consumer-metrics.iotime-total",
                "consumer-metrics.last-poll-seconds-ago",
                "consumer-metrics.network-io-rate",
                "consumer-metrics.network-io-total",
                "consumer-metrics.outgoing-byte-rate",
                "consumer-metrics.outgoing-byte-total",
                "consumer-metrics.poll-idle-ratio-avg",
                "consumer-metrics.reauthentication-latency-avg",
                "consumer-metrics.reauthentication-latency-max",
                "consumer-metrics.request-rate",
                "consumer-metrics.request-size-avg",
                "consumer-metrics.request-size-max",
                "consumer-metrics.request-total",
                "consumer-metrics.response-rate",
                "consumer-metrics.response-total",
                "consumer-metrics.select-rate",
                "consumer-metrics.select-total",
                "consumer-metrics.successful-authentication-no-reauth-total",
                "consumer-metrics.successful-authentication-rate",
                "consumer-metrics.successful-authentication-total",
                "consumer-metrics.successful-reauthentication-rate",
                "consumer-metrics.successful-reauthentication-total",
                "consumer-metrics.time-between-poll-avg",
                "consumer-metrics.time-between-poll-max",
                "consumer-node-metrics.incoming-byte-rate",
                "consumer-node-metrics.incoming-byte-total",
                "consumer-node-metrics.outgoing-byte-rate",
                "consumer-node-metrics.outgoing-byte-total",
                "consumer-node-metrics.request-latency-avg",
                "consumer-node-metrics.request-latency-max",
                "consumer-node-metrics.request-rate",
                "consumer-node-metrics.request-size-avg",
                "consumer-node-metrics.request-size-max",
                "consumer-node-metrics.request-total",
                "consumer-node-metrics.response-rate",
                "consumer-node-metrics.response-total",
                "kafka-metrics-count.count",
                "producer-metrics.batch-size-avg",
                "producer-metrics.batch-size-max",
                "producer-metrics.batch-split-rate",
                "producer-metrics.batch-split-total",
                "producer-metrics.buffer-available-bytes",
                "producer-metrics.buffer-exhausted-rate",
                "producer-metrics.buffer-exhausted-total",
                "producer-metrics.buffer-total-bytes",
                "producer-metrics.bufferpool-wait-ratio",
                "producer-metrics.bufferpool-wait-time-total",
                "producer-metrics.compression-rate-avg",
                "producer-metrics.connection-close-rate",
                "producer-metrics.connection-close-total",
                "producer-metrics.connection-count",
                "producer-metrics.connection-creation-rate",
                "producer-metrics.connection-creation-total",
                "producer-metrics.failed-authentication-rate",
                "producer-metrics.failed-authentication-total",
                "producer-metrics.failed-reauthentication-rate",
                "producer-metrics.failed-reauthentication-total",
                "producer-metrics.incoming-byte-rate",
                "producer-metrics.incoming-byte-total",
                "producer-metrics.io-ratio",
                "producer-metrics.io-time-ns-avg",
                "producer-metrics.io-wait-ratio",
                "producer-metrics.io-wait-time-ns-avg",
                "producer-metrics.io-waittime-total",
                "producer-metrics.iotime-total",
                "producer-metrics.metadata-age",
                "producer-metrics.network-io-rate",
                "producer-metrics.network-io-total",
                "producer-metrics.outgoing-byte-rate",
                "producer-metrics.outgoing-byte-total",
                "producer-metrics.produce-throttle-time-avg",
                "producer-metrics.produce-throttle-time-max",
                "producer-metrics.reauthentication-latency-avg",
                "producer-metrics.reauthentication-latency-max",
                "producer-metrics.record-error-rate",
                "producer-metrics.record-error-total",
                "producer-metrics.record-queue-time-avg",
                "producer-metrics.record-queue-time-max",
                "producer-metrics.record-retry-rate",
                "producer-metrics.record-retry-total",
                "producer-metrics.record-send-rate",
                "producer-metrics.record-send-total",
                "producer-metrics.record-size-avg",
                "producer-metrics.record-size-max",
                "producer-metrics.records-per-request-avg",
                "producer-metrics.request-latency-avg",
                "producer-metrics.request-latency-max",
                "producer-metrics.request-rate",
                "producer-metrics.request-size-avg",
                "producer-metrics.request-size-max",
                "producer-metrics.request-total",
                "producer-metrics.requests-in-flight",
                "producer-metrics.response-rate",
                "producer-metrics.response-total",
                "producer-metrics.select-rate",
                "producer-metrics.select-total",
                "producer-metrics.successful-authentication-no-reauth-total",
                "producer-metrics.successful-authentication-rate",
                "producer-metrics.successful-authentication-total",
                "producer-metrics.successful-reauthentication-rate",
                "producer-metrics.successful-reauthentication-total",
                "producer-metrics.waiting-threads",
                "producer-node-metrics.incoming-byte-rate",
                "producer-node-metrics.incoming-byte-total",
                "producer-node-metrics.outgoing-byte-rate",
                "producer-node-metrics.outgoing-byte-total",
                "producer-node-metrics.request-latency-avg",
                "producer-node-metrics.request-latency-max",
                "producer-node-metrics.request-rate",
                "producer-node-metrics.request-size-avg",
                "producer-node-metrics.request-size-max",
                "producer-node-metrics.request-total",
                "producer-node-metrics.response-rate",
                "producer-node-metrics.response-total",
                "producer-topic-metrics.byte-rate",
                "producer-topic-metrics.byte-total",
                "producer-topic-metrics.compression-rate",
                "producer-topic-metrics.record-error-rate",
                "producer-topic-metrics.record-error-total",
                "producer-topic-metrics.record-retry-rate",
                "producer-topic-metrics.record-retry-total",
                "producer-topic-metrics.record-send-rate",
                "producer-topic-metrics.record-send-total"));

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
