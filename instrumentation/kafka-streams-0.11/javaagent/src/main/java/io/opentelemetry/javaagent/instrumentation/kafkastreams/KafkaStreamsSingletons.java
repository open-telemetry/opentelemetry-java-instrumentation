/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class KafkaStreamsSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-streams-0.11";

  private static final Instrumenter<ConsumerRecord<?, ?>, Void> INSTRUMENTER = buildInstrumenter();

  private static Instrumenter<ConsumerRecord<?, ?>, Void> buildInstrumenter() {
    return KafkaInstrumenterBuilder.buildConsumerProcessInstrumenter(INSTRUMENTATION_NAME);
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private KafkaStreamsSingletons() {}
}
