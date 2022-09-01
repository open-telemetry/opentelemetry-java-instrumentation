/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetryMetricsReporter;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private static final boolean PROPAGATION_ENABLED =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.kafka.client-propagation.enabled", true);

  private static final Instrumenter<ProducerRecord<?, ?>, Void> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<ConsumerRecords<?, ?>, Void> CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<ConsumerRecord<?, ?>, Void> CONSUMER_PROCESS_INSTRUMENTER;

  static {
    KafkaInstrumenterFactory instrumenterFactory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setCaptureExperimentalSpanAttributes(
                InstrumentationConfig.get()
                    .getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false))
            .setPropagationEnabled(PROPAGATION_ENABLED)
            .setMessagingReceiveInstrumentationEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());
    PRODUCER_INSTRUMENTER = instrumenterFactory.createProducerInstrumenter();
    CONSUMER_RECEIVE_INSTRUMENTER = instrumenterFactory.createConsumerReceiveInstrumenter();
    CONSUMER_PROCESS_INSTRUMENTER = instrumenterFactory.createConsumerProcessInstrumenter();
  }

  public static boolean isPropagationEnabled() {
    return PROPAGATION_ENABLED;
  }

  public static Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerRecords<?, ?>, Void> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  public static Map<String, ?> metricConfigProperties() {
    Map<String, Object> config = new HashMap<>();
    config.put(
        CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
        OpenTelemetryMetricsReporter.class.getName());
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTANCE, GlobalOpenTelemetry.get());
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME,
        INSTRUMENTATION_NAME);
    return Collections.unmodifiableMap(config);
  }

  private KafkaSingletons() {}
}
