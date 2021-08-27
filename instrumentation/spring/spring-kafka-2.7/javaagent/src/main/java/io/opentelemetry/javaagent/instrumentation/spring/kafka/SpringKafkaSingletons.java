/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.javaagent.instrumentation.kafka.KafkaConsumerAdditionalAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.kafka.KafkaConsumerAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.kafka.KafkaConsumerExperimentalAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.kafka.KafkaHeadersGetter;
import org.apache.kafka.clients.consumer.ConsumerRecord;

public final class SpringKafkaSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-kafka-2.7";

  private static final Instrumenter<ConsumerRecord<?, ?>, Void> RECEIVE_INSTRUMENTER =
      buildReceiveInstrumenter();
  private static final Instrumenter<BatchRecords<?, ?>, Void> PROCESS_INSTRUMENTER =
      buildProcessInstrumenter();

  private static Instrumenter<ConsumerRecord<?, ?>, Void> buildReceiveInstrumenter() {
    KafkaConsumerAttributesExtractor consumerAttributesExtractor =
        new KafkaConsumerAttributesExtractor(MessageOperation.RECEIVE);
    SpanNameExtractor<ConsumerRecord<?, ?>> spanNameExtractor =
        MessagingSpanNameExtractor.create(consumerAttributesExtractor);

    InstrumenterBuilder<ConsumerRecord<?, ?>, Void> builder =
        Instrumenter.<ConsumerRecord<?, ?>, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(consumerAttributesExtractor)
            .addAttributesExtractor(new KafkaConsumerAdditionalAttributesExtractor());

    if (KafkaConsumerExperimentalAttributesExtractor.isEnabled()) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }

    return builder.newConsumerInstrumenter(new KafkaHeadersGetter());
  }

  private static Instrumenter<BatchRecords<?, ?>, Void> buildProcessInstrumenter() {
    BatchConsumerAttributesExtractor attributesExtractor = new BatchConsumerAttributesExtractor();
    SpanNameExtractor<BatchRecords<?, ?>> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    return Instrumenter.<BatchRecords<?, ?>, Void>newBuilder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addSpanLinksExtractor(BatchRecords.spanLinksExtractor())
        .setErrorCauseExtractor(new KafkaBatchErrorCauseExtractor())
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> receiveInstrumenter() {
    return RECEIVE_INSTRUMENTER;
  }

  public static Instrumenter<BatchRecords<?, ?>, Void> processInstrumenter() {
    return PROCESS_INSTRUMENTER;
  }

  private SpringKafkaSingletons() {}
}
