/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static java.util.Collections.emptyList;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaInstrumenterFactory;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;

public final class KafkaStreamsSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-streams-0.11";

  private static final Instrumenter<KafkaProcessRequest, Void> INSTRUMENTER;

  static {
    ExtendedDeclarativeConfigProperties instrumentationConfig =
        DeclarativeConfigUtil.get(GlobalOpenTelemetry.get());
    INSTRUMENTER =
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
                    .getBoolean("enabled", false))
            .createConsumerProcessInstrumenter();
  }

  public static Instrumenter<KafkaProcessRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private KafkaStreamsSingletons() {}
}
