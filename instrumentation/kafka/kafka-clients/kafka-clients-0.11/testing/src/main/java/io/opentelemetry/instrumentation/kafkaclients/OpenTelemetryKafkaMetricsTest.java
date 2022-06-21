/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
abstract class OpenTelemetryKafkaMetricsTest {

  private static final List<String> TOPICS = Arrays.asList("foo", "bar", "baz", "qux");
  private static final Random RANDOM = new Random();

  @Container
  KafkaContainer kafka =
      new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
          .withLogConsumer(
              new Slf4jLogConsumer(LoggerFactory.getLogger(OpenTelemetryKafkaMetricsTest.class)))
          .waitingFor(Wait.forLogMessage(".*started \\(kafka.server.KafkaServer\\).*", 1))
          .withStartupTimeout(Duration.ofMinutes(1));

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
                "kafka.consumer.commit_latency_avg",
                "kafka.consumer.commit_latency_max",
                "kafka.consumer.commit_rate",
                "kafka.consumer.commit_total",
                "kafka.consumer.failed_rebalance_rate_per_hour",
                "kafka.consumer.failed_rebalance_total",
                "kafka.consumer.heartbeat_rate",
                "kafka.consumer.heartbeat_response_time_max",
                "kafka.consumer.heartbeat_total",
                "kafka.consumer.join_rate",
                "kafka.consumer.join_time_avg",
                "kafka.consumer.join_time_max",
                "kafka.consumer.join_total",
                "kafka.consumer.last_heartbeat_seconds_ago",
                "kafka.consumer.last_rebalance_seconds_ago",
                "kafka.consumer.partition_assigned_latency_avg",
                "kafka.consumer.partition_assigned_latency_max",
                "kafka.consumer.partition_lost_latency_avg",
                "kafka.consumer.partition_lost_latency_max",
                "kafka.consumer.partition_revoked_latency_avg",
                "kafka.consumer.partition_revoked_latency_max",
                "kafka.consumer.rebalance_latency_avg",
                "kafka.consumer.rebalance_latency_max",
                "kafka.consumer.rebalance_latency_total",
                "kafka.consumer.rebalance_rate_per_hour",
                "kafka.consumer.rebalance_total",
                "kafka.consumer.sync_rate",
                "kafka.consumer.sync_time_avg",
                "kafka.consumer.sync_time_max",
                "kafka.consumer.sync_total",
                "kafka.consumer.bytes_consumed_rate",
                "kafka.consumer.bytes_consumed_total",
                "kafka.consumer.fetch_latency_avg",
                "kafka.consumer.fetch_latency_max",
                "kafka.consumer.fetch_rate",
                "kafka.consumer.fetch_size_avg",
                "kafka.consumer.fetch_size_max",
                "kafka.consumer.fetch_throttle_time_avg",
                "kafka.consumer.fetch_throttle_time_max",
                "kafka.consumer.fetch_total",
                "kafka.consumer.records_consumed_rate",
                "kafka.consumer.records_consumed_total",
                "kafka.consumer.records_lag",
                "kafka.consumer.records_lag_avg",
                "kafka.consumer.records_lag_max",
                "kafka.consumer.records_lead",
                "kafka.consumer.records_lead_avg",
                "kafka.consumer.records_lead_min",
                "kafka.consumer.records_per_request_avg",
                "kafka.consumer.connection_close_rate",
                "kafka.consumer.connection_close_total",
                "kafka.consumer.connection_count",
                "kafka.consumer.connection_creation_rate",
                "kafka.consumer.connection_creation_total",
                "kafka.consumer.failed_authentication_rate",
                "kafka.consumer.failed_authentication_total",
                "kafka.consumer.failed_reauthentication_rate",
                "kafka.consumer.failed_reauthentication_total",
                "kafka.consumer.incoming_byte_rate",
                "kafka.consumer.incoming_byte_total",
                "kafka.consumer.io_ratio",
                "kafka.consumer.io_time_ns_avg",
                "kafka.consumer.io_wait_ratio",
                "kafka.consumer.io_wait_time_ns_avg",
                "kafka.consumer.io_waittime_total",
                "kafka.consumer.iotime_total",
                "kafka.consumer.last_poll_seconds_ago",
                "kafka.consumer.network_io_rate",
                "kafka.consumer.network_io_total",
                "kafka.consumer.outgoing_byte_rate",
                "kafka.consumer.outgoing_byte_total",
                "kafka.consumer.poll_idle_ratio_avg",
                "kafka.consumer.reauthentication_latency_avg",
                "kafka.consumer.reauthentication_latency_max",
                "kafka.consumer.request_rate",
                "kafka.consumer.request_size_avg",
                "kafka.consumer.request_size_max",
                "kafka.consumer.request_total",
                "kafka.consumer.response_rate",
                "kafka.consumer.response_total",
                "kafka.consumer.select_rate",
                "kafka.consumer.select_total",
                "kafka.consumer.successful_authentication_no_reauth_total",
                "kafka.consumer.successful_authentication_rate",
                "kafka.consumer.successful_authentication_total",
                "kafka.consumer.successful_reauthentication_rate",
                "kafka.consumer.successful_reauthentication_total",
                "kafka.consumer.time_between_poll_avg",
                "kafka.consumer.time_between_poll_max",
                "kafka.consumer.incoming_byte_rate",
                "kafka.consumer.incoming_byte_total",
                "kafka.consumer.outgoing_byte_rate",
                "kafka.consumer.outgoing_byte_total",
                "kafka.consumer.request_latency_avg",
                "kafka.consumer.request_latency_max",
                "kafka.consumer.request_rate",
                "kafka.consumer.request_size_avg",
                "kafka.consumer.request_size_max",
                "kafka.consumer.request_total",
                "kafka.consumer.response_rate",
                "kafka.consumer.response_total",
                "kafka.producer.batch_size_avg",
                "kafka.producer.batch_size_max",
                "kafka.producer.batch_split_rate",
                "kafka.producer.batch_split_total",
                "kafka.producer.buffer_available_bytes",
                "kafka.producer.buffer_exhausted_rate",
                "kafka.producer.buffer_exhausted_total",
                "kafka.producer.buffer_total_bytes",
                "kafka.producer.bufferpool_wait_ratio",
                "kafka.producer.bufferpool_wait_time_total",
                "kafka.producer.compression_rate_avg",
                "kafka.producer.connection_close_rate",
                "kafka.producer.connection_close_total",
                "kafka.producer.connection_count",
                "kafka.producer.connection_creation_rate",
                "kafka.producer.connection_creation_total",
                "kafka.producer.failed_authentication_rate",
                "kafka.producer.failed_authentication_total",
                "kafka.producer.failed_reauthentication_rate",
                "kafka.producer.failed_reauthentication_total",
                "kafka.producer.incoming_byte_rate",
                "kafka.producer.incoming_byte_total",
                "kafka.producer.io_ratio",
                "kafka.producer.io_time_ns_avg",
                "kafka.producer.io_wait_ratio",
                "kafka.producer.io_wait_time_ns_avg",
                "kafka.producer.io_waittime_total",
                "kafka.producer.iotime_total",
                "kafka.producer.metadata_age",
                "kafka.producer.network_io_rate",
                "kafka.producer.network_io_total",
                "kafka.producer.outgoing_byte_rate",
                "kafka.producer.outgoing_byte_total",
                "kafka.producer.produce_throttle_time_avg",
                "kafka.producer.produce_throttle_time_max",
                "kafka.producer.reauthentication_latency_avg",
                "kafka.producer.reauthentication_latency_max",
                "kafka.producer.record_error_rate",
                "kafka.producer.record_error_total",
                "kafka.producer.record_queue_time_avg",
                "kafka.producer.record_queue_time_max",
                "kafka.producer.record_retry_rate",
                "kafka.producer.record_retry_total",
                "kafka.producer.record_send_rate",
                "kafka.producer.record_send_total",
                "kafka.producer.record_size_avg",
                "kafka.producer.record_size_max",
                "kafka.producer.records_per_request_avg",
                "kafka.producer.request_latency_avg",
                "kafka.producer.request_latency_max",
                "kafka.producer.request_rate",
                "kafka.producer.request_size_avg",
                "kafka.producer.request_size_max",
                "kafka.producer.request_total",
                "kafka.producer.requests_in_flight",
                "kafka.producer.response_rate",
                "kafka.producer.response_total",
                "kafka.producer.select_rate",
                "kafka.producer.select_total",
                "kafka.producer.successful_authentication_no_reauth_total",
                "kafka.producer.successful_authentication_rate",
                "kafka.producer.successful_authentication_total",
                "kafka.producer.successful_reauthentication_rate",
                "kafka.producer.successful_reauthentication_total",
                "kafka.producer.waiting_threads",
                "kafka.producer.incoming_byte_rate",
                "kafka.producer.incoming_byte_total",
                "kafka.producer.outgoing_byte_rate",
                "kafka.producer.outgoing_byte_total",
                "kafka.producer.request_latency_avg",
                "kafka.producer.request_latency_max",
                "kafka.producer.request_rate",
                "kafka.producer.request_size_avg",
                "kafka.producer.request_size_max",
                "kafka.producer.request_total",
                "kafka.producer.response_rate",
                "kafka.producer.response_total",
                "kafka.producer.byte_rate",
                "kafka.producer.byte_total",
                "kafka.producer.compression_rate",
                "kafka.producer.record_error_rate",
                "kafka.producer.record_error_total",
                "kafka.producer.record_retry_rate",
                "kafka.producer.record_retry_total",
                "kafka.producer.record_send_rate",
                "kafka.producer.record_send_total"));

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
    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
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
    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
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
