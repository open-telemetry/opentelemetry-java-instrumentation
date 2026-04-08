/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka.v2_7;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaReceiveRequest;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.internal.SpringKafkaErrorCauseExtractor;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public class SpringKafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";

  private static final SpringKafkaTelemetry telemetry =
      SpringKafkaTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
          .setCaptureExperimentalSpanAttributes(
              DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "kafka")
                  .getBoolean("experimental_span_attributes/development", false))
          .setMessagingReceiveTelemetryEnabled(
              ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
          .build();
  private static final Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter;

  static {
    KafkaInstrumenterFactory factory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setCaptureExperimentalSpanAttributes(
                DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "kafka")
                    .getBoolean("experimental_span_attributes/development", false))
            .setMessagingReceiveTelemetryEnabled(
                ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
            .setErrorCauseExtractor(SpringKafkaErrorCauseExtractor.INSTANCE);
    batchProcessInstrumenter = factory.createBatchProcessInstrumenter();
  }

  public static SpringKafkaTelemetry telemetry() {
    return telemetry;
  }

  public static Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter() {
    return batchProcessInstrumenter;
  }

  private SpringKafkaSingletons() {}
}
