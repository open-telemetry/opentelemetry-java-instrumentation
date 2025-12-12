/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public final class KafkaStreamsSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-streams-0.11";

  private static final Instrumenter<KafkaProcessRequest, Void> INSTRUMENTER =
      new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
          .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
          .setCaptureExperimentalSpanAttributes(
              DeclarativeConfigUtil.getBoolean(
                      GlobalOpenTelemetry.get(), "java", "kafka", "experimental_span_attributes")
                  .orElse(false))
          .setMessagingReceiveInstrumentationEnabled(
              ExperimentalConfig.get().messagingReceiveInstrumentationEnabled())
          .createConsumerProcessInstrumenter();

  public static Instrumenter<KafkaProcessRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private KafkaStreamsSingletons() {}
}
