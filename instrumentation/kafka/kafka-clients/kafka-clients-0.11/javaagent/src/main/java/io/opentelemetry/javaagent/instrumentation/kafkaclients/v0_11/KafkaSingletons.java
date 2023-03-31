/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafka.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafka.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetryMetricsReporter;
import io.opentelemetry.instrumentation.kafka.internal.OpenTelemetrySupplier;
import io.opentelemetry.javaagent.bootstrap.internal.DeprecatedConfigProperties;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.kafka.clients.CommonClientConfigs;
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

  private static final Pattern METRIC_REPORTER_PRESENT_PATTERN =
      Pattern.compile(
          "(^|,)" + Pattern.quote(OpenTelemetryMetricsReporter.class.getName()) + "($|,)");

  private static final Instrumenter<KafkaProducerRequest, RecordMetadata> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<KafkaReceiveRequest, Void> CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<KafkaProcessRequest, Void> CONSUMER_PROCESS_INSTRUMENTER;

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

  public static Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter() {
    return CONSUMER_RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter() {
    return CONSUMER_PROCESS_INSTRUMENTER;
  }

  public static void enhanceConfig(Map<? super String, Object> config) {
    if (!METRICS_ENABLED) {
      return;
    }
    config.merge(
        CommonClientConfigs.METRIC_REPORTER_CLASSES_CONFIG,
        OpenTelemetryMetricsReporter.class.getName(),
        (class1, class2) -> {
          if (class1 instanceof String
              && METRIC_REPORTER_PRESENT_PATTERN.matcher((String) class1).find()) {
            return class1;
          }
          return class1 + "," + class2;
        });
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_SUPPLIER,
        new OpenTelemetrySupplier(GlobalOpenTelemetry.get()));
    config.put(
        OpenTelemetryMetricsReporter.CONFIG_KEY_OPENTELEMETRY_INSTRUMENTATION_NAME,
        INSTRUMENTATION_NAME);
  }

  private KafkaSingletons() {}
}
