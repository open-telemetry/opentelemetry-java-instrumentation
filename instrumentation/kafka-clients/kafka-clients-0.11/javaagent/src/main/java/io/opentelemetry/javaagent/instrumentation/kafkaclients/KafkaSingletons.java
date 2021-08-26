/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

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
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private static final Instrumenter<ProducerRecord<?, ?>, Void> PRODUCER_INSTRUMENTER =
      buildProducerInstrumenter();
  private static final Instrumenter<ConsumerRecord<?, ?>, Void> CONSUMER_INSTRUMENTER =
      buildConsumerInstrumenter();

  private static Instrumenter<ProducerRecord<?, ?>, Void> buildProducerInstrumenter() {
    KafkaProducerAttributesExtractor attributesExtractor = new KafkaProducerAttributesExtractor();
    SpanNameExtractor<ProducerRecord<?, ?>> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    return Instrumenter.<ProducerRecord<?, ?>, Void>newBuilder(
            GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addAttributesExtractor(new KafkaProducerAdditionalAttributesExtractor())
        .newInstrumenter(SpanKindExtractor.alwaysProducer());
  }

  private static Instrumenter<ConsumerRecord<?, ?>, Void> buildConsumerInstrumenter() {
    KafkaConsumerAttributesExtractor attributesExtractor =
        new KafkaConsumerAttributesExtractor(MessageOperation.PROCESS);
    SpanNameExtractor<ConsumerRecord<?, ?>> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    InstrumenterBuilder<ConsumerRecord<?, ?>, Void> builder =
        Instrumenter.<ConsumerRecord<?, ?>, Void>newBuilder(
                GlobalOpenTelemetry.get(), INSTRUMENTATION_NAME, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(new KafkaConsumerAdditionalAttributesExtractor());
    if (KafkaConsumerExperimentalAttributesExtractor.isEnabled()) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }
    return KafkaPropagation.isPropagationEnabled()
        ? builder.newConsumerInstrumenter(new KafkaHeadersGetter())
        : builder.newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public static Instrumenter<ProducerRecord<?, ?>, Void> producerInstrumenter() {
    return PRODUCER_INSTRUMENTER;
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> consumerInstrumenter() {
    return CONSUMER_INSTRUMENTER;
  }

  private KafkaSingletons() {}
}
