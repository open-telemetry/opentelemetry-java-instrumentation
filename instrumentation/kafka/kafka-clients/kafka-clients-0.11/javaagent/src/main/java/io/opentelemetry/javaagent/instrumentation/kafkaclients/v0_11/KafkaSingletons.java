/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.ConsumerAndRecord;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetryMetricsReporter;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetrySupplier;
import io.opentelemetry.javaagent.bootstrap.internal.DeprecatedConfigProperties;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public final class KafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private static final boolean PRODUCER_PROPAGATION_ENABLED =
      DeprecatedConfigProperties.getBoolean(
          InstrumentationConfig.get(),
          "otel.instrumentation.kafka.client-propagation.enabled",
          "otel.instrumentation.kafka.producer-propagation.enabled",
          true);
  private static final boolean METRICS_ENABLED =
      InstrumentationConfig.get()
          .getBoolean("otel.instrumentation.kafka.metric-reporter.enabled", true);

  private static final Instrumenter<ProducerRecord<?, ?>, RecordMetadata> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<ConsumerAndRecord<ConsumerRecords<?, ?>>, Void>
      CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void>
      CONSUMER_PROCESS_INSTRUMENTER;

  static {
    KafkaInstrumenterFactory instrumenterFactory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setCaptureExperimentalSpanAttributes(
                InstrumentationConfig.get()
                    .getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false))
            .setMessagingReceiveInstrumentationEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());
    PRODUCER_INSTRUMENTER = instrumenterFactory.createProducerInstrumenter();
    CONSUMER_RECEIVE_INSTRUMENTER = instrumenterFactory.createConsumerReceiveInstrumenter();
    CONSUMER_PROCESS_INSTRUMENTER = instrumenterFactory.createConsumerProcessInstrumenter();
  }

  public static boolean isProducerPropagationEnabled() {
    return PRODUCER_PROPAGATION_ENABLED;
  }

  public static Instrumenter<ProducerRecord<?, ?>, RecordMetadata> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerAndRecord<ConsumerRecords<?, ?>>, Void>
      consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerAndRecord<ConsumerRecord<?, ?>>, Void>
      consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  public static void enhanceConfig(Map<? super String, Object> config) {
    if (!METRICS_ENABLED) {
      return;
    }
    config.merge(
        CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
        OpenTelemetryMetricsReporter.class.getName(),
        (class1, class2) -> class1 + "," + class2);
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_SUPPLIER,
        new OpenTelemetrySupplier(GlobalOpenTelemetry.get()));
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME,
        INSTRUMENTATION_NAME);
  }

  private KafkaSingletons() {}
}
