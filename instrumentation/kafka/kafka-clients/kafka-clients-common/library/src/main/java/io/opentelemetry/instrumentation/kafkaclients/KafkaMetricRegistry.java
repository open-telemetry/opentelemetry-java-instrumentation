/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** A registry mapping kafka metrics to corresponding OpenTelemetry metric definitions. */
class KafkaMetricRegistry {

  private static final String unitBytesPerSecond = "by/s";

  private static final Map<KafkaMetricId, MetricDescriptor> registry = new HashMap<>();

  static {
    registerProducerMetrics();
    registerProducerTopicMetrics();
    registerConsumerMetrics();
  }

  private static void registerProducerMetrics() {
    String group = "producer-metrics";
    Set<String> tagKeys = Collections.singleton("client-id");

    registry.put(
        KafkaMetricId.create("outgoing-byte-rate", group, tagKeys),
        MetricDescriptor.createDoubleGauge(
            "messaging.kafka.producer.outgoing-bytes.rate",
            "The average number of outgoing bytes sent per second to all servers.",
            unitBytesPerSecond));
    registry.put(
        KafkaMetricId.create("response-rate", group, tagKeys),
        MetricDescriptor.createDoubleGauge(
            "messaging.kafka.producer.responses.rate",
            "The average number of responses received per second.",
            "{responses}/s"));
  }

  private static void registerProducerTopicMetrics() {
    String group = "producer-topic-metrics";
    Set<String> tagKeys = new HashSet<>(Arrays.asList("client-id", "topic"));

    registry.put(
        KafkaMetricId.create("byte-rate", group, tagKeys),
        MetricDescriptor.createDoubleGauge(
            "messaging.kafka.producer.bytes.rate",
            "The average number of bytes sent per second for a specific topic.",
            unitBytesPerSecond));
    registry.put(
        KafkaMetricId.create("compression-rate", group, tagKeys),
        MetricDescriptor.createDoubleGauge(
            "messaging.kafka.producer.compression-ratio",
            "The average compression ratio of record batches for a specific topic.",
            "{compression}"));
    registry.put(
        KafkaMetricId.create("record-error-rate", group, tagKeys),
        MetricDescriptor.createDoubleGauge(
            "messaging.kafka.producer.record-error.rate",
            "The average per-second number of record sends that resulted in errors for a specific topic.",
            "{errors}/s"));
    registry.put(
        KafkaMetricId.create("record-retry-rate", group, tagKeys),
        MetricDescriptor.createDoubleGauge(
            "messaging.kafka.producer.record-retry.rate",
            "The average per-second number of retried record sends for a specific topic.",
            "{retries}/s"));
    registry.put(
        KafkaMetricId.create("record-send-rate", group, tagKeys),
        MetricDescriptor.createDoubleGauge(
            "messaging.kafka.producer.record-sent.rate",
            "The average number of records sent per second for a specific topic.",
            "{records_sent}/s"));
  }

  private static void registerConsumerMetrics() {
    String group = "consumer-fetch-manager-metrics";
    Set<String> tagKeys = new HashSet<>(Arrays.asList("client-id", "topic", "partition"));

    registry.put(
        KafkaMetricId.create("records-lag", group, tagKeys),
        MetricDescriptor.createDoubleGauge(
            "messaging.kafka.consumer.lag",
            "Current approximate lag of consumer group at partition of topic.",
            "{lag}"));
  }

  /**
   * Returns the description of the OpenTelemetry metric definition for the kafka metric, or {@code
   * null} if no mapping exists.
   */
  @Nullable
  static MetricDescriptor getRegisteredInstrument(KafkaMetricId kafkaMetricId) {
    return registry.get(kafkaMetricId);
  }

  // Visible for testing
  static Set<MetricDescriptor> getMetricDescriptors() {
    return new HashSet<>(registry.values());
  }

  private KafkaMetricRegistry() {}
}
