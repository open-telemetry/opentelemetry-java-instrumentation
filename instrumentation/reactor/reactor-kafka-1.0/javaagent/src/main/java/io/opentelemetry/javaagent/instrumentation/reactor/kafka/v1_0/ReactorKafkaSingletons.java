/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor.kafka.v1_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import java.util.Collections;

final class ReactorKafkaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.reactor-kafka-1.0";

  private static final Instrumenter<KafkaProcessRequest, Void> PROCESS_INSTRUMENTER =
      new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
          .setCapturedHeaders(
              DeclarativeConfigUtil.getList(
                      GlobalOpenTelemetry.get(), "java", "messaging", "capture_headers/development")
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
                  .orElse(false))
          .createConsumerProcessInstrumenter();

  public static Instrumenter<KafkaProcessRequest, Void> processInstrumenter() {
    return PROCESS_INSTRUMENTER;
  }

  private ReactorKafkaSingletons() {}
}
