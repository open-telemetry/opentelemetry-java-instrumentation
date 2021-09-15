/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.config.ExperimentalConfig;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanLinksExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

@SuppressWarnings("unchecked")
public final class KafkaUtils {

  static Instrumenter<ProducerRecord<?, ?>, Void> buildProducerInstrumenter(
      String instrumentationName) {
    return buildProducerInstrumenter(instrumentationName, GlobalOpenTelemetry.get());
  }

  public static Instrumenter<ProducerRecord<?, ?>, Void> buildProducerInstrumenter(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      AttributesExtractor<ProducerRecord<?, ?>, Void>... extractors) {
    KafkaProducerAttributesExtractor attributesExtractor = new KafkaProducerAttributesExtractor();
    SpanNameExtractor<ProducerRecord<?, ?>> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    return Instrumenter.<ProducerRecord<?, ?>, Void>newBuilder(
            openTelemetry, instrumentationName, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addAttributesExtractors(extractors)
        .addAttributesExtractor(new KafkaProducerAdditionalAttributesExtractor())
        .newInstrumenter(SpanKindExtractor.alwaysProducer());
  }

  static Instrumenter<ReceivedRecords, Void> buildConsumerReceiveInstrumenter(
      String instrumentationName) {
    return buildConsumerReceiveInstrumenter(instrumentationName, GlobalOpenTelemetry.get());
  }

  public static Instrumenter<ReceivedRecords, Void> buildConsumerReceiveInstrumenter(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      AttributesExtractor<ReceivedRecords, Void>... extractors) {
    KafkaReceiveAttributesExtractor attributesExtractor = new KafkaReceiveAttributesExtractor();
    SpanNameExtractor<ReceivedRecords> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    return Instrumenter.<ReceivedRecords, Void>newBuilder(
            openTelemetry, instrumentationName, spanNameExtractor)
        .addAttributesExtractor(attributesExtractor)
        .addAttributesExtractors(extractors)
        .setTimeExtractors(ReceivedRecords::startTime, (request, response, error) -> request.now())
        .setDisabled(ExperimentalConfig.get().suppressMessagingReceiveSpans())
        .newInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  static Instrumenter<ConsumerRecord<?, ?>, Void> buildConsumerProcessInstrumenter(
      String instrumentationName) {
    return buildConsumerProcessInstrumenter(instrumentationName, GlobalOpenTelemetry.get());
  }

  public static Instrumenter<ConsumerRecord<?, ?>, Void> buildConsumerProcessInstrumenter(
      String instrumentationName,
      OpenTelemetry openTelemetry,
      AttributesExtractor<ConsumerRecord<?, ?>, Void>... extractors) {
    KafkaConsumerAttributesExtractor attributesExtractor =
        new KafkaConsumerAttributesExtractor(MessageOperation.PROCESS);
    SpanNameExtractor<ConsumerRecord<?, ?>> spanNameExtractor =
        MessagingSpanNameExtractor.create(attributesExtractor);

    InstrumenterBuilder<ConsumerRecord<?, ?>, Void> builder =
        Instrumenter.<ConsumerRecord<?, ?>, Void>newBuilder(
                openTelemetry, instrumentationName, spanNameExtractor)
            .addAttributesExtractor(attributesExtractor)
            .addAttributesExtractor(new KafkaConsumerAdditionalAttributesExtractor())
            .addAttributesExtractors(extractors);
    if (KafkaConsumerExperimentalAttributesExtractor.isEnabled()) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }

    if (!KafkaPropagation.isPropagationEnabled()) {
      return builder.newInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else if (ExperimentalConfig.get().suppressMessagingReceiveSpans()) {
      return builder.newConsumerInstrumenter(new KafkaConsumerRecordGetter());
    } else {
      builder.addSpanLinksExtractor(
          SpanLinksExtractor.fromUpstreamRequest(
              GlobalOpenTelemetry.getPropagators(), new KafkaConsumerRecordGetter()));
      return builder.newInstrumenter(SpanKindExtractor.alwaysConsumer());
    }
  }

  private KafkaUtils() {}
}
