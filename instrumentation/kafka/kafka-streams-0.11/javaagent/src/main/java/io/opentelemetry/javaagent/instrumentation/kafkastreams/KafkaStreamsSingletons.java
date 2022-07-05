/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class KafkaStreamsSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-streams-0.11";

  private static final Instrumenter<ConsumerRecord<?, ?>, Void> INSTRUMENTER =
      new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
          .setCaptureExperimentalSpanAttributes(
              InstrumentationConfig.get()
                  .getBoolean("otel.instrumentation.kafka.experimental-span-attributes", false))
          .setPropagationEnabled(
              InstrumentationConfig.get()
                  .getBoolean("otel.instrumentation.kafka.client-propagation.enabled", true))
          .createConsumerProcessInstrumenter();

  public static Instrumenter<ConsumerRecord<?, ?>, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private KafkaStreamsSingletons() {}
}
