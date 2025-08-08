/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class KafkaConnectSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-connect-2.6";
  private static final TextMapPropagator PROPAGATOR = 
      GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator();
  
  private static final Instrumenter<KafkaConnectTask, Void> INSTRUMENTER =
      Instrumenter.<KafkaConnectTask, Void>builder(
              GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, KafkaConnectTask::getSpanName)
          .addSpanLinksExtractor(new KafkaConnectBatchProcessSpanLinksExtractor(PROPAGATOR))
          .buildInstrumenter();

  public static Instrumenter<KafkaConnectTask, Void> instrumenter() {
    return INSTRUMENTER;
  }

  public static TextMapPropagator propagator() {
    return PROPAGATOR;
  }

  private KafkaConnectSingletons() {}
}
