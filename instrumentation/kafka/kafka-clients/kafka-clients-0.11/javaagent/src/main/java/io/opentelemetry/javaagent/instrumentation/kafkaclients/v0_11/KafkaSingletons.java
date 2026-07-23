/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaPropagation;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;
import org.apache.kafka.clients.producer.RecordMetadata;

public class KafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  public static final boolean PRODUCER_PROPAGATION_ENABLED =
      DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "kafka")
          .get("producer_propagation")
          .getBoolean("enabled", true);
  public static final boolean PRODUCER_SPAN_CONTEXT_PROPAGATION_ENABLED =
      PRODUCER_PROPAGATION_ENABLED
          && KafkaPropagation.propagatesSpanContext(
              GlobalOpenTelemetry.getPropagators().getTextMapPropagator());

  private static final Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter;
  private static final Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter;
  private static final Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter;

  static {
    DeclarativeConfigProperties commonConfig =
        DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "common");
    Boolean messagingReceiveInstrumentationEnabled =
        commonConfig.get("messaging").get("receive_telemetry/development").getBoolean("enabled");
    KafkaInstrumenterFactory instrumenterFactory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setCaptureExperimentalSpanAttributes(
                DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "kafka")
                    .getBoolean("experimental_span_attributes/development", false));
    if (messagingReceiveInstrumentationEnabled != null) {
      instrumenterFactory.setMessagingReceiveTelemetryEnabled(
          messagingReceiveInstrumentationEnabled);
    }
    producerInstrumenter = instrumenterFactory.createProducerInstrumenter();
    consumerReceiveInstrumenter = instrumenterFactory.createConsumerReceiveInstrumenter();
    consumerProcessInstrumenter = instrumenterFactory.createConsumerProcessInstrumenter();
  }

  public static Instrumenter<KafkaProducerRequest, RecordMetadata> producerInstrumenter() {
    return producerInstrumenter;
  }

  public static Instrumenter<KafkaReceiveRequest, Void> consumerReceiveInstrumenter() {
    return consumerReceiveInstrumenter;
  }

  public static Instrumenter<KafkaProcessRequest, Void> consumerProcessInstrumenter() {
    return consumerProcessInstrumenter;
  }

  private KafkaSingletons() {}
}
