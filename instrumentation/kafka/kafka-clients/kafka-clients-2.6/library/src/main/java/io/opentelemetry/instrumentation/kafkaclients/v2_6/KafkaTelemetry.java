/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.MetricsReporterList;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.OpenTelemetryMetricsReporter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.OpenTelemetrySupplier;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.KafkaHelperSupplier;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.OpenTelemetryConsumerInterceptor;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.OpenTelemetryProducerInterceptor;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.metrics.MetricsReporter;

public final class KafkaTelemetry {

  private final OpenTelemetry openTelemetry;
  private final KafkaHelper helper;

  KafkaTelemetry(
      OpenTelemetry openTelemetry,
      Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter,
      Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter,
      boolean producerPropagationEnabled) {
    this.openTelemetry = openTelemetry;
    this.helper =
        new KafkaHelper(
            openTelemetry.getPropagators().getTextMapPropagator(),
            producerInstrumenter,
            consumerReceiveInstrumenter,
            consumerProcessInstrumenter,
            producerPropagationEnabled);
  }

  @Deprecated
  KafkaHelper getHelper() {
    return helper;
  }

  /** Returns a new {@link KafkaTelemetry} configured with the given {@link OpenTelemetry}. */
  public static KafkaTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link KafkaTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
  public static KafkaTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new KafkaTelemetryBuilder(openTelemetry);
  }

  /**
   * Produces a set of kafka client config properties (consumer or producer) to register a {@link
   * MetricsReporter} that records metrics to an {@code openTelemetry} instance. Add these resulting
   * properties to the configuration map used to initialize a {@link KafkaConsumer} or {@link
   * KafkaProducer}.
   *
   * <p>For producers:
   *
   * <pre>{@code
   * //    Map<String, Object> config = new HashMap<>();
   * //    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
   * //    config.putAll(kafkaTelemetry.metricConfigProperties());
   * //    try (KafkaProducer<?, ?> producer = new KafkaProducer<>(config)) { ... }
   * }</pre>
   *
   * <p>For consumers:
   *
   * <pre>{@code
   * //    Map<String, Object> config = new HashMap<>();
   * //    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
   * //    config.putAll(kafkaTelemetry.metricConfigProperties());
   * //    try (KafkaConsumer<?, ?> consumer = new KafkaConsumer<>(config)) { ... }
   * }</pre>
   *
   * @return the kafka client properties
   */
  public Map<String, ?> metricConfigProperties() {
    Map<String, Object> config = new HashMap<>();
    config.put(
        CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
        MetricsReporterList.singletonList(OpenTelemetryMetricsReporter.class));
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_SUPPLIER,
        new OpenTelemetrySupplier(openTelemetry));
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME,
        KafkaTelemetryBuilder.INSTRUMENTATION_NAME);
    return Collections.unmodifiableMap(config);
  }

  /**
   * Returns configuration properties that can be used to enable OpenTelemetry instrumentation via
   * {@code OpenTelemetryProducerInterceptor}. Add these resulting properties to the configuration
   * map used to initialize a {@link org.apache.kafka.clients.producer.KafkaProducer}.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * //    KafkaTelemetry telemetry = KafkaTelemetry.create(openTelemetry);
   * //    Map<String, Object> config = new HashMap<>();
   * //    config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
   * //    config.putAll(telemetry.producerInterceptorConfigProperties());
   * //    try (KafkaProducer<?, ?> producer = new KafkaProducer<>(config)) { ... }
   * }</pre>
   *
   * @return the kafka producer interceptor config properties
   */
  public Map<String, ?> producerInterceptorConfigProperties() {
    Map<String, Object> config = new HashMap<>();
    config.put(
        ProducerConfig.INTERCEPTOR_CLASSES_CONFIG,
        OpenTelemetryProducerInterceptor.class.getName());
    config.put(
        OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_HELPER_SUPPLIER,
        new KafkaHelperSupplier(helper));
    return Collections.unmodifiableMap(config);
  }

  /**
   * Returns configuration properties that can be used to enable OpenTelemetry instrumentation via
   * {@code OpenTelemetryConsumerInterceptor}. Add these resulting properties to the configuration
   * map used to initialize a {@link org.apache.kafka.clients.consumer.KafkaConsumer}.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * //    KafkaTelemetry telemetry = KafkaTelemetry.create(openTelemetry);
   * //    Map<String, Object> config = new HashMap<>();
   * //    config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, ...);
   * //    config.putAll(telemetry.consumerInterceptorConfigProperties());
   * //    try (KafkaConsumer<?, ?> consumer = new KafkaConsumer<>(config)) { ... }
   * }</pre>
   *
   * @return the kafka consumer interceptor config properties
   */
  public Map<String, ?> consumerInterceptorConfigProperties() {
    Map<String, Object> config = new HashMap<>();
    config.put(
        ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG,
        OpenTelemetryConsumerInterceptor.class.getName());
    config.put(
        OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_HELPER_SUPPLIER,
        new KafkaHelperSupplier(helper));
    return Collections.unmodifiableMap(config);
  }
}
