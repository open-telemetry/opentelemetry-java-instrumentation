/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.Timer;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.MetricsReporterList;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.OpenTelemetryMetricsReporter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.OpenTelemetrySupplier;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.KafkaConsumerTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.KafkaConsumerTelemetrySupplier;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.KafkaProducerTelemetry;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.KafkaProducerTelemetrySupplier;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.OpenTelemetryConsumerInterceptor;
import io.opentelemetry.instrumentation.kafkaclients.v2_6.internal.OpenTelemetryProducerInterceptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.metrics.MetricsReporter;

public final class KafkaTelemetry {

  private final OpenTelemetry openTelemetry;
  private final KafkaProducerTelemetry producerTelemetry;
  private final KafkaConsumerTelemetry consumerTelemetry;

  KafkaTelemetry(
      OpenTelemetry openTelemetry,
      Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter,
      Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter,
      Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter,
      boolean producerPropagationEnabled) {
    this.openTelemetry = openTelemetry;
    this.producerTelemetry =
        new KafkaProducerTelemetry(
            openTelemetry.getPropagators().getTextMapPropagator(),
            producerInstrumenter,
            producerPropagationEnabled);
    this.consumerTelemetry =
        new KafkaConsumerTelemetry(consumerReceiveInstrumenter, consumerProcessInstrumenter);
  }

  // this method can be removed when the deprecated TracingProducerInterceptor is removed
  KafkaProducerTelemetry getProducerTelemetry() {
    return producerTelemetry;
  }

  // this method can be removed when the deprecated TracingProducerInterceptor is removed
  KafkaConsumerTelemetry getConsumerTelemetry() {
    return consumerTelemetry;
  }

  @SuppressWarnings("unchecked")
  public <K, V> Producer<K, V> wrap(Producer<K, V> producer) {
    return (Producer<K, V>)
        Proxy.newProxyInstance(
            KafkaTelemetry.class.getClassLoader(),
            new Class<?>[] {Producer.class},
            (proxy, method, args) -> {
              // Future<RecordMetadata> send(ProducerRecord<K, V> record)
              // Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback)
              if ("send".equals(method.getName())
                  && method.getParameterCount() >= 1
                  && method.getParameterTypes()[0] == ProducerRecord.class) {
                ProducerRecord<K, V> record = (ProducerRecord<K, V>) args[0];
                Callback callback =
                    method.getParameterCount() >= 2
                            && method.getParameterTypes()[1] == Callback.class
                        ? (Callback) args[1]
                        : null;
                return producerTelemetry.buildAndInjectSpan(record, producer, callback, producer::send);
              }
              try {
                return method.invoke(producer, args);
              } catch (InvocationTargetException exception) {
                throw exception.getCause();
              }
            });
  }

  @SuppressWarnings("unchecked")
  public <K, V> Consumer<K, V> wrap(Consumer<K, V> consumer) {
    return (Consumer<K, V>)
        Proxy.newProxyInstance(
            KafkaTelemetry.class.getClassLoader(),
            new Class<?>[] {Consumer.class},
            (proxy, method, args) -> {
              Object result;
              Timer timer = "poll".equals(method.getName()) ? Timer.start() : null;
              try {
                result = method.invoke(consumer, args);
              } catch (InvocationTargetException exception) {
                throw exception.getCause();
              }
              // ConsumerRecords<K, V> poll(long timeout)
              // ConsumerRecords<K, V> poll(Duration duration)
              if ("poll".equals(method.getName()) && result instanceof ConsumerRecords) {
                ConsumerRecords<K, V> consumerRecords = (ConsumerRecords<K, V>) result;
                Context receiveContext =
                    consumerTelemetry.buildAndFinishSpan(consumerRecords, consumer, timer);
                if (receiveContext == null) {
                  receiveContext = Context.current();
                }
                KafkaConsumerContext consumerContext =
                    KafkaConsumerContextUtil.create(receiveContext, consumer);
                result = consumerTelemetry.addTracing(consumerRecords, consumerContext);
              }
              return result;
            });
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
        OpenTelemetryProducerInterceptor.CONFIG_KEY_KAFKA_PRODUCER_TELEMETRY_SUPPLIER,
        new KafkaProducerTelemetrySupplier(producerTelemetry));
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
        OpenTelemetryConsumerInterceptor.CONFIG_KEY_KAFKA_CONSUMER_TELEMETRY_SUPPLIER,
        new KafkaConsumerTelemetrySupplier(consumerTelemetry));
    return Collections.unmodifiableMap(config);
  }
}
