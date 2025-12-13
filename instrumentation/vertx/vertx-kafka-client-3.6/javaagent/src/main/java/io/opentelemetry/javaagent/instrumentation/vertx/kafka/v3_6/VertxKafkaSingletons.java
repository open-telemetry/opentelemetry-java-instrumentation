/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import java.util.Collections;

public final class VertxKafkaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-kafka-client-3.6";

  private static final Instrumenter<KafkaReceiveRequest, Void> BATCH_PROCESS_INSTRUMENTER;
  private static final Instrumenter<KafkaProcessRequest, Void> PROCESS_INSTRUMENTER;

  static {
    KafkaInstrumenterFactory factory =
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
