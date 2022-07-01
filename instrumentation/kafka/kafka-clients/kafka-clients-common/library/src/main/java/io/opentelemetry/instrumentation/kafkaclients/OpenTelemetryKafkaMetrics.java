/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.internal.OpenTelemetryMetricsReporter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.common.metrics.MetricsReporter;

/**
 * Record kafka client metrics to OpenTelemetry.
 *
 * @see #getConfigProperties(OpenTelemetry)
 */
public class OpenTelemetryKafkaMetrics {

  /**
   * Produces a set of kafka client config properties (consumer or producer) to register a {@link
   * MetricsReporter} that records metrics to the {@code openTelemetry} instance. Add these
   * resulting properties to the configuration map used to initialize a {@link KafkaConsumer} or
   * {@link KafkaProducer}.
   *
   * <p>For producers:
   *
   * <pre>{@code
   * //    Map<String, Object> config = new HashMap<>();
   * //    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
   * //    config.putAll(OpenTelemetryKafka.openTelemetryConfigProperties(openTelemetry);
   * //    try (KafkaProducer<?, ?> producer = new KafkaProducer<>(config)) { ... }
   * }</pre>
   *
   * <p>For consumers:
   *
   * <pre>{@code
   * //    Map<String, Object> config = new HashMap<>();
   * //    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
   * //    config.putAll(OpenTelemetryKafka.openTelemetryConfigProperties(openTelemetry);
   * //    try (KafkaConsumer<?, ?> consumer = new KafkaConsumer<>(config)) { ... }
   * }</pre>
   *
   * @param openTelemetry the {@link OpenTelemetry} used to record metrics
   * @return the kafka client properties
   */
  public static Map<String, ?> getConfigProperties(OpenTelemetry openTelemetry) {
    Map<String, Object> config = new HashMap<>();
    config.put(
        CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
        OpenTelemetryMetricsReporter.class.getName());
    config.put(OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTANCE, openTelemetry);
    return Collections.unmodifiableMap(config);
  }

  private OpenTelemetryKafkaMetrics() {}
}
