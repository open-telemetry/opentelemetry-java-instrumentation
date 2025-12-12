/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProducerRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import java.util.Collections;
import org.apache.kafka.clients.producer.RecordMetadata;

public final class KafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private static final boolean PRODUCER_PROPAGATION_ENABLED =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "kafka", "producer_propagation", "enabled")
          .orElse(true);

  private static final Instrumenter<KafkaProducerRequest, RecordMetadata> PRODUCER_INSTRUMENTER;
  private static final Instrumenter<KafkaReceiveRequest, Void> CONSUMER_RECEIVE_INSTRUMENTER;
  private static final Instrumenter<KafkaProcessRequest, Void> CONSUMER_PROCESS_INSTRUMENTER;

  static {
    KafkaInstrumenterFactory instrumenterFactory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(
                DeclarativeConfigUtil.getList(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "messaging",
                        "capture_headers/development")
                    .orElse(Collections.emptyList()))
            .setCaptureExperimentalSpanAttributes(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(), "java", "kafka", "experimental_span_attributes")
                    .orElse(false))
            .setMessagingReceiveInstrumentationEnabled(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(),
                        "java",
                        "messaging",
                        "receive_telemetry/development",
                        "enabled")
                    .orElse(false));
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

  private KafkaSingletons() {}
}
