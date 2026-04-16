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
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public class VertxKafkaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-kafka-client-3.6";

  private static final Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter;
  private static final Instrumenter<KafkaProcessRequest, Void> processInstrumenter;

  static {
    KafkaInstrumenterFactory factory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setCaptureExperimentalSpanAttributes(
                DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "kafka")
                    .getBoolean("experimental_span_attributes/development", false))
            .setMessagingReceiveTelemetryEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());
    batchProcessInstrumenter = factory.createBatchProcessInstrumenter();
    processInstrumenter = factory.createConsumerProcessInstrumenter();
  }

  public static Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter() {
    return batchProcessInstrumenter;
  }

  public static Instrumenter<KafkaProcessRequest, Void> processInstrumenter() {
    return processInstrumenter;
  }

  private VertxKafkaSingletons() {}
}
