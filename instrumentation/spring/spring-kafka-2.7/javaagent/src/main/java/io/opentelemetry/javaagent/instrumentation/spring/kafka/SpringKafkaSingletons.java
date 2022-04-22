/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public final class SpringKafkaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";

  private static final Instrumenter<ConsumerRecords<?, ?>, Void> BATCH_PROCESS_INSTRUMENTER =
      buildBatchProcessInstrumenter();
  private static final Instrumenter<ConsumerRecord<?, ?>, Void> PROCESS_INSTRUMENTER =
      new KafkaInstrumenterFactory(GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME)
          .setErrorCauseExtractor(SpringKafkaErrorCauseExtractor.INSTANCE)
          .createConsumerProcessInstrumenter();

  private static Instrumenter<ConsumerRecords<?, ?>, Void> buildBatchProcessInstrumenter() {
    KafkaBatchProcessAttributesGetter getter = KafkaBatchProcessAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    return Instrumenter.<ConsumerRecords<?, ?>, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(MessagingAttributesExtractor.create(getter, operation))
        .addSpanLinksExtractor(
            new KafkaBatchProcessSpanLinksExtractor(GlobalOpenTelemetry.getPropagators()))
        .setErrorCauseExtractor(SpringKafkaErrorCauseExtractor.INSTANCE)
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<ConsumerRecords<?, ?>, Void> batchProcessInstrumenter() {
    return BATCH_PROCESS_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> processInstrumenter() {
    return PROCESS_INSTRUMENTER;
  }

  private SpringKafkaSingletons() {}
}
