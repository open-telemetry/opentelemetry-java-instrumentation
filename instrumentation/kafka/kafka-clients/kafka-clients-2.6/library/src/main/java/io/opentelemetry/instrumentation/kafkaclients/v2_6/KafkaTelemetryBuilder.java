/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.v2_6;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.kafka.internal.KafkaInstrumenterFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

public final class KafkaTelemetryBuilder {
  static final String INSTRUMENTATION_NAME = "io.opentelemetry.kafka-clients-2.6";

  private final OpenTelemetry openTelemetry;
  private final List<AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata>>
      producerAttributesExtractors = new ArrayList<>();
  private final List<AttributesExtractor<ConsumerRecord<?, ?>, Void>> consumerAttributesExtractors =
      new ArrayList<>();
  private List<String> capturedHeaders = emptyList();
  private boolean captureExperimentalSpanAttributes = false;
  private boolean propagationEnabled = true;

  KafkaTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = Objects.requireNonNull(openTelemetry);
  }

  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder addProducerAttributesExtractors(
      AttributesExtractor<ProducerRecord<?, ?>, RecordMetadata> extractor) {
    producerAttributesExtractors.add(extractor);
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder addConsumerAttributesExtractors(
      AttributesExtractor<ConsumerRecord<?, ?>, Void> extractor) {
    consumerAttributesExtractors.add(extractor);
    return this;
  }

  /**
   * Configures the messaging headers that will be captured as span attributes.
   *
   * @param capturedHeaders A list of messaging header names.
   */
  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder setCapturedHeaders(List<String> capturedHeaders) {
    this.capturedHeaders = capturedHeaders;
    return this;
  }

  /**
   * Sets whether experimental attributes should be set to spans. These attributes may be changed or
   * removed in the future, so only enable this if you know you do not require attributes filled by
   * this instrumentation to be stable across versions.
   */
  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  /**
   * Set whether to propagate trace context in producers. Enabled by default.
   *
   * <p>You will need to disable this if there are kafka consumers using kafka-clients version prior
   * to 0.11, since those old versions do not support headers, and attaching trace context
   * propagation headers upstream causes those consumers to fail when reading the messages.
   */
  @CanIgnoreReturnValue
  public KafkaTelemetryBuilder setPropagationEnabled(boolean propagationEnabled) {
    this.propagationEnabled = propagationEnabled;
    return this;
  }

  public KafkaTelemetry build() {
    KafkaInstrumenterFactory instrumenterFactory =
        new KafkaInstrumenterFactory(openTelemetry, INSTRUMENTATION_NAME)
            .setCapturedHeaders(capturedHeaders)
            .setCaptureExperimentalSpanAttributes(captureExperimentalSpanAttributes);

    return new KafkaTelemetry(
        openTelemetry,
        instrumenterFactory.createProducerInstrumenter(producerAttributesExtractors),
        instrumenterFactory.createConsumerOperationInstrumenter(
            MessageOperation.RECEIVE, consumerAttributesExtractors),
        propagationEnabled);
  }
}
