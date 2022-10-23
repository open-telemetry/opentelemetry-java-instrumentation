/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafka.internal;

import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.internal.PropagatorBasedSpanLinksExtractor;
import java.util.Collections;
import java.util.List;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaInstrumenterFactory {

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.getDefault();
  private List<String> capturedHeaders = emptyList();
  private boolean captureExperimentalSpanAttributes = false;
  private boolean propagationEnabled = true;
  private boolean messagingReceiveInstrumentationEnabled = false;

  public KafkaInstrumenterFactory(OpenTelemetry openTelemetry, String instrumentationName) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setErrorCauseExtractor(ErrorCauseExtractor errorCauseExtractor) {
    this.errorCauseExtractor = errorCauseExtractor;
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setCapturedHeaders(List<String> capturedHeaders) {
    this.capturedHeaders = capturedHeaders;
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setPropagationEnabled(boolean propagationEnabled) {
    this.propagationEnabled = propagationEnabled;
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setMessagingReceiveInstrumentationEnabled(
      boolean messagingReceiveInstrumentationEnabled) {
    this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    return this;
  }

  public Instrumenter<ProducerRecord<?, ?>, Void> createProducerInstrumenter() {
    return createProducerInstrumenter(Collections.emptyList());
  }

  public Instrumenter<ProducerRecord<?, ?>, Void> createProducerInstrumenter(
      Iterable<AttributesExtractor<ProducerRecord<?, ?>, Void>> extractors) {

    KafkaProducerAttributesGetter getter = KafkaProducerAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.SEND;

    return Instrumenter.<ProducerRecord<?, ?>, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(getter, operation, capturedHeaders))
        .addAttributesExtractors(extractors)
        .addAttributesExtractor(new KafkaProducerAdditionalAttributesExtractor())
        .setErrorCauseExtractor(errorCauseExtractor)
        .buildInstrumenter(SpanKindExtractor.alwaysProducer());
  }

  public Instrumenter<ConsumerRecords<?, ?>, Void> createConsumerReceiveInstrumenter() {
    KafkaReceiveAttributesGetter getter = KafkaReceiveAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.RECEIVE;

    return Instrumenter.<ConsumerRecords<?, ?>, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(getter, operation, capturedHeaders))
        .setErrorCauseExtractor(errorCauseExtractor)
        .setEnabled(messagingReceiveInstrumentationEnabled)
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  public Instrumenter<ConsumerRecord<?, ?>, Void> createConsumerProcessInstrumenter() {
    return createConsumerOperationInstrumenter(MessageOperation.PROCESS, Collections.emptyList());
  }

  public Instrumenter<ConsumerRecord<?, ?>, Void> createConsumerOperationInstrumenter(
      MessageOperation operation,
      Iterable<AttributesExtractor<ConsumerRecord<?, ?>, Void>> extractors) {

    KafkaConsumerAttributesGetter getter = KafkaConsumerAttributesGetter.INSTANCE;

    InstrumenterBuilder<ConsumerRecord<?, ?>, Void> builder =
        Instrumenter.<ConsumerRecord<?, ?>, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operation))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(getter, operation, capturedHeaders))
            .addAttributesExtractor(new KafkaConsumerAdditionalAttributesExtractor())
            .addAttributesExtractors(extractors)
            .setErrorCauseExtractor(errorCauseExtractor);
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }

    if (!propagationEnabled) {
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else if (messagingReceiveInstrumentationEnabled) {
      builder.addSpanLinksExtractor(
          new PropagatorBasedSpanLinksExtractor<ConsumerRecord<?, ?>>(
              openTelemetry.getPropagators().getTextMapPropagator(),
              KafkaConsumerRecordGetter.INSTANCE));
      return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
    } else {
      return builder.buildConsumerInstrumenter(KafkaConsumerRecordGetter.INSTANCE);
    }
  }

  public Instrumenter<ConsumerRecords<?, ?>, Void> createBatchProcessInstrumenter() {
    KafkaBatchProcessAttributesGetter getter = KafkaBatchProcessAttributesGetter.INSTANCE;
    MessageOperation operation = MessageOperation.PROCESS;

    return Instrumenter.<ConsumerRecords<?, ?>, Void>builder(
            openTelemetry,
            instrumentationName,
            MessagingSpanNameExtractor.create(getter, operation))
        .addAttributesExtractor(
            buildMessagingAttributesExtractor(getter, operation, capturedHeaders))
        .addSpanLinksExtractor(
            new KafkaBatchProcessSpanLinksExtractor(
                openTelemetry.getPropagators().getTextMapPropagator()))
        .setErrorCauseExtractor(errorCauseExtractor)
        .buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static <T> MessagingAttributesExtractor<T, Void> buildMessagingAttributesExtractor(
      MessagingAttributesGetter<T, Void> getter,
      MessageOperation operation,
      List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builder(getter, operation)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }
}
