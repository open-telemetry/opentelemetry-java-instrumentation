/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import io.opentelemetry.javaagent.bootstrap.internal.ExperimentalConfig;

public class KafkaStreamsSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-streams-0.11";

  private static final Instrumenter<KafkaProcessRequest, Void> instrumenter = createInstrumenter();

  private static Instrumenter<KafkaProcessRequest, Void> createInstrumenter() {
    KafkaInstrumenterFactory factory =
        new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
            .setCapturedHeaders(ExperimentalConfig.get().getMessagingHeaders())
            .setCaptureExperimentalSpanAttributes(
                DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "kafka")
                    .getBoolean("experimental_span_attributes/development", false));
    Boolean receiveTelemetryEnabled =
        ExperimentalConfig.get().messagingReceiveInstrumentationEnabled();
    if (receiveTelemetryEnabled != null) {
      factory.setMessagingReceiveTelemetryEnabled(receiveTelemetryEnabled);
    }
    return factory.createConsumerProcessInstrumenter();
  }

  public static Instrumenter<KafkaProcessRequest, Void> instrumenter() {
    return instrumenter;
  }

  private KafkaStreamsSingletons() {}
}
