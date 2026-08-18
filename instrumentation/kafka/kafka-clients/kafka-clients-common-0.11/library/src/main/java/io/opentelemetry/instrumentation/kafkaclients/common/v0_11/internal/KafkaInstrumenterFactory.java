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
import static java.util.Collections.singletonList;

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
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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
  // copied from ErrorAttributes
  private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");
  private static final ContextKey<Long> CONSUMED_MESSAGES_COUNT_KEY =
      ContextKey.named("opentelemetry-kafka-consumed-messages-count");
  private static final ContextKey<DeliveryTracker.DeliveryState> CONSUMED_MESSAGES_DELIVERY_STATE =
      ContextKey.named("opentelemetry-kafka-consumed-messages-delivery");
  private static final OperationMetrics consumedMessagesMetrics =
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
            DeliveryTracker.DeliveryState deliveryState =
                context.get(CONSUMED_MESSAGES_DELIVERY_STATE);
            if (deliveryState != null) {
              endDeliveryTracking(deliveryState, endAttributes.get(ERROR_TYPE) == null);
            }
          }
        };
      };

  private final OpenTelemetry openTelemetry;
  private final String instrumentationName;
  private ErrorCauseExtractor errorCauseExtractor = ErrorCauseExtractor.getDefault();
  private IncludeExclude headers = IncludeExclude.builder().build();
  private boolean captureExperimentalSpanAttributes = false;
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
    addConsumedMessagesIfNoReceiveOperation(builder);
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
   */
  private static void addConsumedMessagesIfNoReceiveOperation(
      InstrumenterBuilder<KafkaProcessRequest, Void> builder) {
    if (emitStableMessagingSemconv()) {
      builder
          .addContextCustomizer(
              (context, request, startAttributes) ->
                  startDeliveryTracking(
                      context, request, request.getRecord(), deliveryKeys(request)))
          .addOperationMetrics(consumedMessagesMetrics);
    }
  }

  private static void addBatchConsumedMessagesIfNoReceiveOperation(
      InstrumenterBuilder<KafkaReceiveRequest, Void> builder) {
    if (emitStableMessagingSemconv()) {
      builder
          .addContextCustomizer(
              (context, request, startAttributes) ->
                  startBatchDeliveryTracking(
                      context, request, request.getRecords(), deliveryKeys(request)))
          .addOperationMetrics(consumedMessagesMetrics);
    }
  }

  private static Context startDeliveryTracking(
      Context context,
      AbstractKafkaConsumerRequest request,
      ConsumerRecord<?, ?> delivery,
      List<DeliveryTracker.DeliveryKey> deliveryKeys) {
    DeliveryTracker deliveryTracker = request.getDeliveryTracker();
    DeliveryTracker.DeliveryState deliveryState = startDeliveryState(deliveryTracker, deliveryKeys);
    long consumedMessagesCount =
        KafkaConsumerContextUtil.hasReceiveOperation(context)
            ? 0
            : countConsumedMessages(delivery, deliveryState);
    return withDeliveryState(context, deliveryState)
        .with(CONSUMED_MESSAGES_COUNT_KEY, consumedMessagesCount);
  }

  private static Context startBatchDeliveryTracking(
      Context context,
      AbstractKafkaConsumerRequest request,
      Iterable<? extends ConsumerRecord<?, ?>> deliveries,
      List<DeliveryTracker.DeliveryKey> deliveryKeys) {
    DeliveryTracker deliveryTracker = request.getDeliveryTracker();
    DeliveryTracker.DeliveryState deliveryState = startDeliveryState(deliveryTracker, deliveryKeys);
    long consumedMessagesCount =
        KafkaConsumerContextUtil.hasReceiveOperation(context)
            ? 0
            : countConsumedMessages(deliveries, deliveryState);
    return withDeliveryState(context, deliveryState)
        .with(CONSUMED_MESSAGES_COUNT_KEY, consumedMessagesCount);
  }

  @Nullable
  private static DeliveryTracker.DeliveryState startDeliveryState(
      @Nullable DeliveryTracker deliveryTracker, List<DeliveryTracker.DeliveryKey> deliveryKeys) {
    return deliveryTracker == null ? null : deliveryTracker.start(deliveryKeys);
  }

  private static Context withDeliveryState(
      Context context, @Nullable DeliveryTracker.DeliveryState deliveryState) {
    if (deliveryState == null) {
      return context;
    }
    return context.with(CONSUMED_MESSAGES_DELIVERY_STATE, deliveryState);
  }

  private static void addReceiveConsumedMessages(
      InstrumenterBuilder<KafkaReceiveRequest, Void> builder) {
    builder
        .addContextCustomizer(
            (context, request, startAttributes) ->
                context.with(
                    CONSUMED_MESSAGES_COUNT_KEY,
                    countConsumedMessages(
                        request.getRecords(), deliveryKeys(request), request.getDeliveryTracker())))
        .addOperationMetrics(consumedMessagesMetrics);
  }

  private static long countConsumedMessages(
      ConsumerRecord<?, ?> delivery, @Nullable DeliveryTracker.DeliveryState deliveryState) {
    if (!KafkaConsumerContextUtil.markConsumedMessageCounted(delivery)) {
      return 0;
    }
    return deliveryState != null && deliveryState.wasPendingFailed(0) ? 0 : 1;
  }

  private static long countConsumedMessages(
      Iterable<? extends ConsumerRecord<?, ?>> deliveries,
      @Nullable DeliveryTracker.DeliveryState deliveryState) {
    long consumedMessagesCount = 0;
    int index = 0;
    for (ConsumerRecord<?, ?> delivery : deliveries) {
      if (KafkaConsumerContextUtil.markConsumedMessageCounted(delivery)
          && (deliveryState == null || !deliveryState.wasPendingFailed(index))) {
        consumedMessagesCount++;
      }
      index++;
    }
    return consumedMessagesCount;
  }

  private static long countConsumedMessages(
      Iterable<? extends ConsumerRecord<?, ?>> deliveries,
      List<DeliveryTracker.DeliveryKey> deliveryKeys,
      @Nullable DeliveryTracker deliveryTracker) {
    long consumedMessagesCount = 0;
    int index = 0;
    for (ConsumerRecord<?, ?> delivery : deliveries) {
      if (KafkaConsumerContextUtil.markConsumedMessageCounted(delivery)
          && !isPendingFailed(deliveryTracker, deliveryKeys.get(index))) {
        consumedMessagesCount++;
      }
      index++;
    }
    return consumedMessagesCount;
  }

  /**
   * A delivery with no tracker is always counted, because without a tracker a redelivery cannot be
   * recognized.
   */
  private static boolean isPendingFailed(
      @Nullable DeliveryTracker deliveryTracker, DeliveryTracker.DeliveryKey deliveryKey) {
    return deliveryTracker != null && deliveryTracker.isPendingFailed(deliveryKey);
  }

  private static void endDeliveryTracking(DeliveryTracker.DeliveryState state, boolean successful) {
    state.end(successful);
  }

  private static List<DeliveryTracker.DeliveryKey> deliveryKeys(KafkaProcessRequest request) {
    return singletonList(deliveryKey(request.getRecord()));
  }

  private static List<DeliveryTracker.DeliveryKey> deliveryKeys(KafkaReceiveRequest request) {
    List<DeliveryTracker.DeliveryKey> keys = new ArrayList<>();
    for (ConsumerRecord<?, ?> record : request.getRecords()) {
      keys.add(deliveryKey(record));
    }
    return keys;
  }

  /**
   * Returns a key that tells deliveries apart. A {@link DeliveryTracker} belongs to one consumer,
   * so the topic, partition and offset are enough to identify a delivery within it.
   */
  private static DeliveryTracker.DeliveryKey deliveryKey(ConsumerRecord<?, ?> record) {
    return new DeliveryTracker.DeliveryKey(record.topic(), record.partition(), record.offset());
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
    addBatchConsumedMessagesIfNoReceiveOperation(builder);
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
