/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;

public final class KafkaTelemetryBuilder {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-2.6";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<ProducerRecord<?, ?>, Void>> producerAttributesExtractors =
      new ArrayList<>();
  private final List<AttributesExtractor<ConsumerRecord<?, ?>, Void>> consumerAttributesExtractors =
      new ArrayList<>();

  KafkaTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = Objects.requireNonNull(openTelemetry);
  }

  public KafkaTelemetryBuilder addProducerAttributesExtractors(
      AttributesExtractor<ProducerRecord<?, ?>, Void> extractor) {
    producerAttributesExtractors.add(extractor);
    return this;
  }

  public KafkaTelemetryBuilder addConsumerAttributesExtractors(
      AttributesExtractor<ConsumerRecord<?, ?>, Void> extractor) {
    consumerAttributesExtractors.add(extractor);
    return this;
  }

  public KafkaTelemetry build() {
    return new KafkaTelemetry(
        openTelemetry,
        KafkaInstrumenterFactory.createProducerInstrumenter(
            INSTRUMENTATION_NAME, openTelemetry, producerAttributesExtractors),
        KafkaInstrumenterFactory.createConsumerOperationInstrumenter(
            INSTRUMENTATION_NAME,
            openTelemetry,
            MessageOperation.RECEIVE,
            consumerAttributesExtractors));
  }
}
