/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public final class SpringKafkaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";

  private static final Instrumenter<ConsumerRecords<?, ?>, Void> PROCESS_INSTRUMENTER =
      buildProcessInstrumenter();

  private static Instrumenter<ConsumerRecords<?, ?>, Void> buildProcessInstrumenter() {
    KafkaBatchProcessAttributesExtractor attributesExtractor =
        new KafkaBatchProcessAttributesExtractor();
    SpanNameExtractor<ConsumerRecords<?, ?>> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    return Instrumenter.<ConsumerRecords<?, ?>, Void>builder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addSpanLinksExtractor(
            new KafkaBatchProcessSpanLinksExtractor(GlobalOpenTelemetry.getPropagators()))
        .setErrorCauseExtractor(new KafkaBatchErrorCauseExtractor())
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> receiveInstrumenter() {
    return null;
  }

  public static Instrumenter<ConsumerRecords<?, ?>, Void> processInstrumenter() {
    return PROCESS_INSTRUMENTER;
  }

  private SpringKafkaSingletons() {}
}
