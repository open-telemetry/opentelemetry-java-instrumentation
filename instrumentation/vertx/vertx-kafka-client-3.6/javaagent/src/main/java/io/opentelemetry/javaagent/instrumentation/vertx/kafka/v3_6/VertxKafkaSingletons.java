/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;

public final class VertxKafkaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-kafka-client-3.6";

  private static final Instrumenter<KafkaReceiveRequest, Void> BATCH_PROCESS_INSTRUMENTER;
  private static final Instrumenter<KafkaProcessRequest, Void> PROCESS_INSTRUMENTER;

  static {
    ExtendedDeclarativeConfigProperties instrumentationConfig =
        DeclarativeConfigUtil.get(GlobalOpenTelemetry.get());
    KafkaInstrumenterFactory factory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(
                instrumentationConfig
                    .get("messaging")
                    .getScalarList("capture_headers/development", String.class, emptyList()))
            .setCaptureExperimentalSpanAttributes(
                instrumentationConfig
                    .get("kafka")
                    .getBoolean("experimental_span_attributes", false))
            .setMessagingReceiveInstrumentationEnabled(
                instrumentationConfig
                    .get("messaging")
                    .get("receive_telemetry/development")
                    .getBoolean("enabled", false));
    BATCH_PROCESS_INSTRUMENTER = factory.createBatchProcessInstrumenter();
    PROCESS_INSTRUMENTER = factory.createConsumerProcessInstrumenter();
  }

  public static Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter() {
    return BATCH_PROCESS_INSTRUMENTER;
  }

  public static Instrumenter<KafkaProcessRequest, Void> processInstrumenter() {
    return PROCESS_INSTRUMENTER;
  }

  private VertxKafkaSingletons() {}
}
