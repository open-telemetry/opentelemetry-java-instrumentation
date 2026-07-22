/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal;

import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingProcessExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingReceiveExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingExceptionEventExtractors.setMessagingSendExceptionEventExtractor;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaInstrumenterFactory {

  private static final String SEND_OPERATION_NAME = "send";
  private static final String POLL_OPERATION_NAME = "poll";

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.getDefault();
  private List<String> capturedHeaders = emptyList();
  private boolean captureExperimentalSpanAttributes = false;
  private boolean messagingReceiveInstrumentationEnabled = false;
  private boolean messagingReceiveInstrumentationConfigured = false;

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
  public KafkaInstrumenterFactory setCapturedHeaders(Collection<String> capturedHeaders) {
    this.capturedHeaders = new ArrayList<>(capturedHeaders);
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setCaptureExperimentalSpanAttributes(
      boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setMessagingReceiveTelemetryEnabled(
      boolean messagingReceiveInstrumentationEnabled) {
    this.messagingReceiveInstrumentationEnabled = messagingReceiveInstrumentationEnabled;
    this.messagingReceiveInstrumentationConfigured = true;
    return this;
  }

  public Instrumenter<KafkaProducerRequest, RecordMetadata> createProducerInstrumenter() {
    return createProducerInstrumenter(emptyList());
  }

  public Instrumenter<KafkaProducerRequest, RecordMetadata> createProducerInstrumenter(
      Iterable<AttributesExtractor<KafkaProducerRequest, RecordMetadata>> extractors) {

    KafkaProducerAttributesGetter getter = new KafkaProducerAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.SEND;

    InstrumenterBuilder<KafkaProducerRequest, RecordMetadata> builder =
        Instrumenter.<KafkaProducerRequest, RecordMetadata>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.builder(getter, operationType)
                    .setOperationName(SEND_OPERATION_NAME)
                    .build())
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, SEND_OPERATION_NAME, capturedHeaders))
            .addAttributesExtractors(extractors)
            .addAttributesExtractor(new KafkaProducerAttributesExtractor())
            .setErrorCauseExtractor(errorCauseExtractor);
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new KafkaProducerExperimentalAttributesExtractor());
    }
    setMessagingSendExceptionEventExtractor(builder);
    return builder.buildInstrumenter(
        request ->
            emitStableMessagingSemconv() && !request.isSpanContextPropagated()
                ? SpanKind.CLIENT
                : SpanKind.PRODUCER);
  }

  public Instrumenter<KafkaReceiveRequest, Void> createConsumerReceiveInstrumenter() {
    return createConsumerReceiveInstrumenter(emptyList());
  }

  public Instrumenter<KafkaReceiveRequest, Void> createConsumerReceiveInstrumenter(
      Iterable<AttributesExtractor<KafkaReceiveRequest, Void>> extractors) {
    KafkaReceiveAttributesGetter getter = new KafkaReceiveAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.RECEIVE;
    boolean receiveInstrumentationEnabled = receiveInstrumentationEnabled();

    InstrumenterBuilder<KafkaReceiveRequest, Void> builder =
        Instrumenter.<KafkaReceiveRequest, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.builder(getter, operationType)
                    .setOperationName(POLL_OPERATION_NAME)
                    .build())
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, POLL_OPERATION_NAME, capturedHeaders))
            .addAttributesExtractor(new KafkaReceiveAttributesExtractor())
            .addAttributesExtractors(extractors)
            .setErrorCauseExtractor(errorCauseExtractor)
            .setEnabled(receiveInstrumentationEnabled);
    if (emitStableMessagingSemconv()) {
      builder.addSpanLinksExtractor(
          new KafkaBatchProcessSpanLinksExtractor(
              openTelemetry.getPropagators().getTextMapPropagator()));
    }
    setMessagingReceiveExceptionEventExtractor(builder);
    return builder.buildInstrumenter(MessagingSpanKindExtractor.create(operationType));
  }

  public Instrumenter<KafkaProcessRequest, Void> createConsumerProcessInstrumenter() {
    return createConsumerProcessInstrumenter(emptyList());
  }

  public Instrumenter<KafkaProcessRequest, Void> createConsumerProcessInstrumenter(
      Iterable<AttributesExtractor<KafkaProcessRequest, Void>> extractors) {
    KafkaConsumerAttributesGetter getter = new KafkaConsumerAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<KafkaProcessRequest, Void> builder =
        Instrumenter.<KafkaProcessRequest, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operationType))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(getter, operationType, capturedHeaders))
            .addAttributesExtractor(new KafkaConsumerAttributesExtractor())
            .addAttributesExtractors(extractors)
            .setErrorCauseExtractor(errorCauseExtractor);
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }
    setMessagingProcessExceptionEventExtractor(builder);

    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        new KafkaConsumerRecordGetter(),
        receiveInstrumentationEnabled());
  }

  private boolean receiveInstrumentationEnabled() {
    return messagingReceiveInstrumentationConfigured
        ? messagingReceiveInstrumentationEnabled
        : emitStableMessagingSemconv();
  }

  public Instrumenter<KafkaReceiveRequest, Void> createBatchProcessInstrumenter() {
    KafkaReceiveAttributesGetter getter = new KafkaReceiveAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<KafkaReceiveRequest, Void> builder =
        Instrumenter.<KafkaReceiveRequest, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operationType))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(getter, operationType, capturedHeaders))
            .addAttributesExtractor(new KafkaReceiveAttributesExtractor())
            .addSpanLinksExtractor(
                new KafkaBatchProcessSpanLinksExtractor(
                    openTelemetry.getPropagators().getTextMapPropagator()))
            .setErrorCauseExtractor(errorCauseExtractor);
    setMessagingProcessExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static <REQUEST, RESPONSE>
      AttributesExtractor<REQUEST, RESPONSE> buildMessagingAttributesExtractor(
          MessagingAttributesGetter<REQUEST, RESPONSE> getter,
          MessagingOperationType operationType,
          String operationName,
          List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builderForOperationType(getter, operationType)
        .setOperationName(operationName)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }

  private static <REQUEST, RESPONSE>
      AttributesExtractor<REQUEST, RESPONSE> buildMessagingAttributesExtractor(
          MessagingAttributesGetter<REQUEST, RESPONSE> getter,
          MessagingOperationType operationType,
          List<String> capturedHeaders) {
    return MessagingAttributesExtractor.builderForOperationType(getter, operationType)
        .setCapturedHeaders(capturedHeaders)
        .build();
  }
}
