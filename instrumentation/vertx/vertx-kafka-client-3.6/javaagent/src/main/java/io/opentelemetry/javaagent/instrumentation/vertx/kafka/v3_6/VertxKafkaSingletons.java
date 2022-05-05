/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.kafka.v3_6;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class VertxKafkaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vertx-kafka-client-3.5";

  private static final Instrumenter<ConsumerRecord<?, ?>, Void> PROCESS_INSTRUMENTER =
      new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
          .createConsumerProcessInstrumenter();

  public static Instrumenter<ConsumerRecord<?, ?>, Void> processInstrumenter() {
    return PROCESS_INSTRUMENTER;
  }

  private VertxKafkaSingletons() {}
}
