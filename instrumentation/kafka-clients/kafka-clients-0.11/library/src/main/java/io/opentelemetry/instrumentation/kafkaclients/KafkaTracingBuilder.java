/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.kafka.KafkaUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaTracingBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-0.11";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<ProducerRecord<?, ?>, Void>> producerAttributesExtractors =
      new ArrayList<>();
  private final List<AttributesExtractor<ConsumerRecord<?, ?>, Void>>
      consumerProcessAttributesExtractors = new ArrayList<>();

  KafkaTracingBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = Objects.requireNonNull(openTelemetry);
  }

  public void addProducerAttributesExtractors(
      AttributesExtractor<ProducerRecord<?, ?>, Void> extractor) {
    producerAttributesExtractors.add(extractor);
  }

  public void addConsumerAttributesProcessExtractors(
      AttributesExtractor<ConsumerRecord<?, ?>, Void> extractor) {
    consumerProcessAttributesExtractors.add(extractor);
  }

  @SuppressWarnings("unchecked")
  public KafkaTracing build() {
    return new KafkaTracing(
        KafkaUtils.buildProducerInstrumenter(
            INSTRUMENTATION_NAME,
            openTelemetry,
            producerAttributesExtractors.toArray(new AttributesExtractor[0])),
        KafkaUtils.buildConsumerOperationInstrumenter(
            INSTRUMENTATION_NAME,
            openTelemetry,
            MessageOperation.RECEIVE,
            consumerProcessAttributesExtractors.toArray(new AttributesExtractor[0])));
  }
}
