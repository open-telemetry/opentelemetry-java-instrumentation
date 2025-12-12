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
import java.util.Collections;
import java.util.List;

public final class SpringKafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "kafka", "experimental_span_attributes")
          .orElse(false);

  private static final List<String> CAPTURED_HEADERS =
      DeclarativeConfigUtil.getList(
              GlobalOpenTelemetry.get(), "java", "messaging", "capture_headers/development")
          .orElse(Collections.emptyList());

  private static final boolean RECEIVE_TELEMETRY_ENABLED =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(),
              "java",
              "messaging",
              "receive_telemetry/development",
              "enabled")
          .orElse(false);

  private static final SpringKafkaTelemetry TELEMETRY =
      SpringKafkaTelemetry.builder(GlobalOpenTelemetry.get())
          .setCapturedHeaders(CAPTURED_HEADERS)
          .setCaptureExperimentalSpanAttributes(CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES)
          .setMessagingReceiveInstrumentationEnabled(RECEIVE_TELEMETRY_ENABLED)
          .build();
  private static final Instrumenter<KafkaReceiveRequest, Void> BATCH_PROCESS_INSTRUMENTER;

  static {
    KafkaInstrumenterFactory factory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(CAPTURED_HEADERS)
            .setCaptureExperimentalSpanAttributes(CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES)
            .setMessagingReceiveInstrumentationEnabled(RECEIVE_TELEMETRY_ENABLED)
            .setErrorCauseExtractor(SpringKafkaErrorCauseExtractor.INSTANCE);
    BATCH_PROCESS_INSTRUMENTER = factory.createBatchProcessInstrumenter();
  }

  public static SpringKafkaTelemetry telemetry() {
    return TELEMETRY;
  }

  public static Instrumenter<KafkaReceiveRequest, Void> batchProcessInstrumenter() {
    return BATCH_PROCESS_INSTRUMENTER;
  }

  private SpringKafkaSingletons() {}
}
