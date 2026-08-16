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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingConsumerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingOperationType;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProcessMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingProducerMetrics;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanKindExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingProcessInstrumenterFactory;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationListener;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.instrumentation.api.internal.cache.Cache;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.ToLongFunction;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.RecordMetadata;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class KafkaInstrumenterFactory {

  private static final String SEND_OPERATION_NAME = "send";
  private static final String POLL_OPERATION_NAME = "poll";
  private static final String PROCESS_OPERATION_NAME = "process";
  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      AttributeKey.longKey("messaging.batch.message_count");
  private static final ContextKey<Long> CONSUMED_MESSAGES_COUNT_KEY =
      ContextKey.named("opentelemetry-kafka-consumed-messages-count");
  private static final Cache<OpenTelemetry, Cache<Object, AtomicBoolean>>
      countedDeliveryObjectsCache = Cache.weak();

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private final Cache<Object, AtomicBoolean> countedDeliveryObjects;
  private final OperationMetrics consumedMessagesMetrics =
      meter -> {
        OperationListener delegate = MessagingConsumerMetrics.getConsumedMessages().create(meter);
        return new OperationListener() {
          @Override
          public Context onStart(Context context, Attributes startAttributes, long startNanos) {
            Long consumedMessagesCount = context.get(CONSUMED_MESSAGES_COUNT_KEY);
            return consumedMessagesCount != null && consumedMessagesCount > 0
                ? delegate.onStart(context, startAttributes, startNanos)
                : context;
          }

          @Override
          public void onEnd(Context context, Attributes endAttributes, long endNanos) {
            Long consumedMessagesCount = context.get(CONSUMED_MESSAGES_COUNT_KEY);
            if (consumedMessagesCount != null && consumedMessagesCount > 0) {
              delegate.onEnd(
                  context,
                  withConsumedMessagesCount(endAttributes, consumedMessagesCount),
                  endNanos);
            }
          }
        };
      };

  private ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.getDefault();
  private IncludeExclude headers = IncludeExclude.builder().build();
  private boolean captureExperimentalSpanAttributes = false;
  private boolean messagingReceiveInstrumentationEnabled = false;

  public KafkaInstrumenterFactory(OpenTelemetry openTelemetry, String instrumentationName) {
    this.openTelemetry = openTelemetry;
    this.instrumentationName = instrumentationName;
    countedDeliveryObjects =
        countedDeliveryObjectsCache.computeIfAbsent(openTelemetry, unused -> Cache.weak());
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setErrorCauseExtractor(ErrorCauseExtractor errorCauseExtractor) {
    this.errorCauseExtractor = errorCauseExtractor;
    return this;
  }

  @CanIgnoreReturnValue
  public KafkaInstrumenterFactory setHeaders(IncludeExclude headers) {
    this.headers = headers;
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
    return this;
  }

  public Instrumenter<KafkaProducerRequest, RecordMetadata> createProducerInstrumenter() {
    return createProducerInstrumenter(emptyList());
  }

  public Instrumenter<KafkaProducerRequest, RecordMetadata> createProducerInstrumenter(
      Iterable<AttributesExtractor<KafkaProducerRequest, RecordMetadata>> extractors) {
    return createProducerInstrumenter(extractors, MessagingProducerMetrics.getForOperationType());
  }

  private Instrumenter<KafkaProducerRequest, RecordMetadata> createProducerInstrumenter(
      Iterable<AttributesExtractor<KafkaProducerRequest, RecordMetadata>> extractors,
      OperationMetrics operationMetrics) {
    KafkaProducerAttributesGetter getter = new KafkaProducerAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.SEND;

    InstrumenterBuilder<KafkaProducerRequest, RecordMetadata> builder =
        Instrumenter.<KafkaProducerRequest, RecordMetadata>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operationType, SEND_OPERATION_NAME))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, SEND_OPERATION_NAME, headers))
            .addAttributesExtractors(extractors)
            .addAttributesExtractor(new KafkaProducerAttributesExtractor())
            .addOperationMetrics(operationMetrics)
            .setErrorCauseExtractor(errorCauseExtractor);
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new KafkaProducerExperimentalAttributesExtractor());
    }
    setMessagingSendExceptionEventExtractor(builder);
    return builder.buildInstrumenter(
        MessagingSpanKindExtractor.create(
            operationType, KafkaProducerRequest::isSpanContextPropagated));
  }

  // the producer interceptor returns from onSend before the record is sent to the broker, and its
  // onAcknowledgement hook does not report the outcome back, so the span covers only the header
  // injection. timing it would be misleading, so this instrumenter records only the sent messages
  // counter.
  public Instrumenter<KafkaProducerRequest, RecordMetadata> createProducerInterceptorInstrumenter(
      Iterable<AttributesExtractor<KafkaProducerRequest, RecordMetadata>> extractors) {
    return createProducerInstrumenter(extractors, MessagingProducerMetrics.getSentMessages());
  }

  public Instrumenter<KafkaReceiveRequest, Void> createConsumerReceiveInstrumenter() {
    return createConsumerReceiveInstrumenter(emptyList());
  }

  public Instrumenter<KafkaReceiveRequest, Void> createConsumerReceiveInstrumenter(
      Iterable<AttributesExtractor<KafkaReceiveRequest, Void>> extractors) {
    return createConsumerReceiveInstrumenter(extractors, true);
  }

  private Instrumenter<KafkaReceiveRequest, Void> createConsumerReceiveInstrumenter(
      Iterable<AttributesExtractor<KafkaReceiveRequest, Void>> extractors,
      boolean addClientOperationDuration) {
    KafkaReceiveAttributesGetter getter = new KafkaReceiveAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.RECEIVE;
    boolean receiveInstrumentationEnabled = receiveInstrumentationEnabled();

    InstrumenterBuilder<KafkaReceiveRequest, Void> builder =
        Instrumenter.<KafkaReceiveRequest, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operationType, POLL_OPERATION_NAME))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, POLL_OPERATION_NAME, headers))
            .addAttributesExtractor(new KafkaReceiveAttributesExtractor())
            .addAttributesExtractors(extractors)
            .setErrorCauseExtractor(errorCauseExtractor)
            .setEnabled(receiveInstrumentationEnabled);
    if (addClientOperationDuration) {
      builder.addOperationMetrics(MessagingConsumerMetrics.getClientOperationDuration());
    }
    if (emitStableMessagingSemconv()) {
      addReceiveConsumedMessages(builder);
      builder.addSpanLinksExtractor(
          new KafkaBatchProcessSpanLinksExtractor(
              openTelemetry.getPropagators().getTextMapPropagator()));
    }
    setMessagingReceiveExceptionEventExtractor(builder);
    return builder.buildInstrumenter(MessagingSpanKindExtractor.create(operationType));
  }

  // the consumer interceptor runs onConsume after the poll has already returned, so the interceptor
  // can not measure how long the poll took. timing it would be misleading, so this instrumenter
  // does not record the client operation duration.
  public Instrumenter<KafkaReceiveRequest, Void> createConsumerReceiveInterceptorInstrumenter(
      Iterable<AttributesExtractor<KafkaReceiveRequest, Void>> extractors) {
    return createConsumerReceiveInstrumenter(extractors, false);
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
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, PROCESS_OPERATION_NAME, headers))
            .addAttributesExtractor(new KafkaConsumerAttributesExtractor())
            .addAttributesExtractors(extractors)
            .addOperationMetrics(MessagingProcessMetrics.get())
            .setErrorCauseExtractor(errorCauseExtractor);
    if (captureExperimentalSpanAttributes) {
      builder.addAttributesExtractor(new KafkaConsumerExperimentalAttributesExtractor());
    }
    addConsumedMessagesIfNoReceiveOperation(
        builder, request -> countConsumedMessages(request.getRecord(), 1));
    setMessagingProcessExceptionEventExtractor(builder);

    return MessagingProcessInstrumenterFactory.create(
        builder,
        openTelemetry.getPropagators().getTextMapPropagator(),
        new KafkaConsumerRecordGetter(),
        receiveInstrumentationEnabled());
  }

  private boolean receiveInstrumentationEnabled() {
    return messagingReceiveInstrumentationEnabled;
  }

  /**
   * Records {@code messaging.client.consumed.messages} on a process operation when the delivery was
   * not counted by a receive operation. Semantic conventions require the counter to be reported
   * once per message delivery, including push-based dispatch such as listener callbacks, which have
   * no receive operation.
   *
   * <p>The count is deduplicated per individual {@link ConsumerRecord}, so that a batch process
   * operation and the per-record process operations of the same delivery, which both run in some
   * frameworks, together count each record exactly once.
   */
  private <REQUEST> void addConsumedMessagesIfNoReceiveOperation(
      InstrumenterBuilder<REQUEST, Void> builder, ToLongFunction<REQUEST> messageCounter) {
    if (emitStableMessagingSemconv()) {
      builder
          .addContextCustomizer(
              (context, request, startAttributes) -> {
                long consumedMessagesCount =
                    KafkaConsumerContextUtil.hasReceiveOperation(context)
                        ? 0
                        : messageCounter.applyAsLong(request);
                return context.with(CONSUMED_MESSAGES_COUNT_KEY, consumedMessagesCount);
              })
          .addOperationMetrics(consumedMessagesMetrics);
    }
  }

  private void addReceiveConsumedMessages(InstrumenterBuilder<KafkaReceiveRequest, Void> builder) {
    builder
        .addContextCustomizer(
            (context, request, startAttributes) -> {
              return context.with(
                  CONSUMED_MESSAGES_COUNT_KEY, countConsumedMessages(request.getRecords()));
            })
        .addOperationMetrics(consumedMessagesMetrics);
  }

  /**
   * Returns {@code messageCount} the first time the given delivery object is seen, and {@code 0}
   * afterwards, so that operations that observe the same delivery do not count it twice.
   */
  private long countConsumedMessages(Object delivery, long messageCount) {
    boolean firstDeliveryObject =
        countedDeliveryObjects
            .computeIfAbsent(delivery, unused -> new AtomicBoolean())
            .compareAndSet(false, true);
    return firstDeliveryObject ? messageCount : 0;
  }

  /** Counts the records of a batch individually, so that they can be deduplicated one by one. */
  private long countConsumedMessages(ConsumerRecords<?, ?> records) {
    long consumedMessagesCount = 0;
    for (ConsumerRecord<?, ?> record : records) {
      consumedMessagesCount += countConsumedMessages(record, 1);
    }
    return consumedMessagesCount;
  }

  public Instrumenter<KafkaReceiveRequest, Void> createBatchProcessInstrumenter() {
    KafkaReceiveAttributesGetter getter = new KafkaReceiveAttributesGetter();
    MessagingOperationType operationType = MessagingOperationType.PROCESS;

    InstrumenterBuilder<KafkaReceiveRequest, Void> builder =
        Instrumenter.<KafkaReceiveRequest, Void>builder(
                openTelemetry,
                instrumentationName,
                MessagingSpanNameExtractor.create(getter, operationType, PROCESS_OPERATION_NAME))
            .addAttributesExtractor(
                buildMessagingAttributesExtractor(
                    getter, operationType, PROCESS_OPERATION_NAME, headers))
            .addAttributesExtractor(new KafkaReceiveAttributesExtractor())
            .addSpanLinksExtractor(
                new KafkaBatchProcessSpanLinksExtractor(
                    openTelemetry.getPropagators().getTextMapPropagator()))
            .addOperationMetrics(MessagingProcessMetrics.get())
            .setErrorCauseExtractor(errorCauseExtractor);
    addConsumedMessagesIfNoReceiveOperation(
        builder, request -> countConsumedMessages(request.getRecords()));
    setMessagingProcessExceptionEventExtractor(builder);
    return builder.buildInstrumenter(SpanKindExtractor.alwaysConsumer());
  }

  private static Attributes withConsumedMessagesCount(
      Attributes attributes, long consumedMessagesCount) {
    return attributes.toBuilder().put(MESSAGING_BATCH_MESSAGE_COUNT, consumedMessagesCount).build();
  }

  private static <REQUEST, RESPONSE>
      AttributesExtractor<REQUEST, RESPONSE> buildMessagingAttributesExtractor(
          MessagingAttributesGetter<REQUEST, RESPONSE> getter,
          MessagingOperationType operationType,
          String operationName,
          IncludeExclude headers) {
    return MessagingAttributesExtractor.builder(getter, operationType, operationName)
        .setHeaders(headers)
        .build();
  }
}
