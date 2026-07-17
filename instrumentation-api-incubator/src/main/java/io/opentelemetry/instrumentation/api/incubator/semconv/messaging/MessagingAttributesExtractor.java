/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/v1.43.0/docs/messaging/messaging-spans.md">messaging
 * attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link MessagingAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class MessagingAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE>, SpanKeyProvider {

  // copied from MessagingIncubatingAttributes
  private static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      AttributeKey.longKey("messaging.batch.message_count");
  private static final AttributeKey<String> MESSAGING_CLIENT_ID_OLD =
      AttributeKey.stringKey("messaging.client_id");
  private static final AttributeKey<String> MESSAGING_CLIENT_ID =
      AttributeKey.stringKey("messaging.client.id");
  private static final AttributeKey<Boolean> MESSAGING_DESTINATION_ANONYMOUS =
      AttributeKey.booleanKey("messaging.destination.anonymous");
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_DESTINATION_PARTITION_ID =
      AttributeKey.stringKey("messaging.destination.partition.id");
  private static final AttributeKey<String> MESSAGING_DESTINATION_TEMPLATE =
      AttributeKey.stringKey("messaging.destination.template");
  private static final AttributeKey<Boolean> MESSAGING_DESTINATION_TEMPORARY =
      AttributeKey.booleanKey("messaging.destination.temporary");
  private static final AttributeKey<Long> MESSAGING_MESSAGE_BODY_SIZE =
      AttributeKey.longKey("messaging.message.body.size");
  private static final AttributeKey<String> MESSAGING_MESSAGE_CONVERSATION_ID =
      AttributeKey.stringKey("messaging.message.conversation_id");
  private static final AttributeKey<Long> MESSAGING_MESSAGE_ENVELOPE_SIZE =
      AttributeKey.longKey("messaging.message.envelope.size");
  private static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      AttributeKey.stringKey("messaging.message.id");
  private static final AttributeKey<String> MESSAGING_OPERATION =
      AttributeKey.stringKey("messaging.operation");
  private static final AttributeKey<String> MESSAGING_OPERATION_NAME =
      AttributeKey.stringKey("messaging.operation.name");
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");

  static final String TEMP_DESTINATION_NAME = "(temporary)";

  /** Creates the messaging attributes extractor for the given operation type. */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      @Nullable MessagingOperationType operationType) {
    return builder(getter, operationType).build();
  }

  /**
   * @deprecated Use {@link #create(MessagingAttributesGetter, MessagingOperationType)}. Will be
   *     removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter, @Nullable MessageOperation operation) {
    return create(getter, operation == null ? null : operation.type());
  }

  /**
   * Returns a new {@link MessagingAttributesExtractorBuilder} configured for the given operation
   * type.
   */
  public static <REQUEST, RESPONSE> MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      @Nullable MessagingOperationType operationType) {
    return new MessagingAttributesExtractorBuilder<>(getter, operationType);
  }

  /**
   * @deprecated Use {@link #builder(MessagingAttributesGetter, MessagingOperationType)}. Will be
   *     removed in 3.0.
   */
  @Deprecated // to be removed in 3.0
  public static <REQUEST, RESPONSE> MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter, @Nullable MessageOperation operation) {
    return builder(getter, operation == null ? null : operation.type());
  }

  private final MessagingAttributesGetter<REQUEST, RESPONSE> getter;
  @Nullable private final MessagingOperationType operationType;
  @Nullable private final String operationName;
  private final List<String> capturedHeaders;

  MessagingAttributesExtractor(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      @Nullable MessagingOperationType operationType,
      @Nullable String operationName,
      List<String> capturedHeaders) {
    this.getter = getter;
    this.operationType = operationType;
    this.operationName = operationName;
    this.capturedHeaders = new ArrayList<>(capturedHeaders);
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(MESSAGING_SYSTEM, getter.getSystem(request));
    boolean isTemporaryDestination = getter.isTemporaryDestination(request);
    if (isTemporaryDestination) {
      attributes.put(MESSAGING_DESTINATION_TEMPORARY, true);
      if (emitStableMessagingSemconv()) {
        attributes.put(MESSAGING_DESTINATION_NAME, getter.getDestination(request));
        attributes.put(MESSAGING_DESTINATION_TEMPLATE, getter.getDestinationTemplate(request));
      } else {
        attributes.put(MESSAGING_DESTINATION_NAME, TEMP_DESTINATION_NAME);
      }
    } else {
      attributes.put(MESSAGING_DESTINATION_NAME, getter.getDestination(request));
      attributes.put(MESSAGING_DESTINATION_TEMPLATE, getter.getDestinationTemplate(request));
    }
    attributes.put(MESSAGING_DESTINATION_PARTITION_ID, getter.getDestinationPartitionId(request));
    boolean isAnonymousDestination = getter.isAnonymousDestination(request);
    if (isAnonymousDestination) {
      attributes.put(MESSAGING_DESTINATION_ANONYMOUS, true);
    }
    attributes.put(MESSAGING_MESSAGE_CONVERSATION_ID, getter.getConversationId(request));
    attributes.put(MESSAGING_MESSAGE_BODY_SIZE, getter.getMessageBodySize(request));
    attributes.put(MESSAGING_MESSAGE_ENVELOPE_SIZE, getter.getMessageEnvelopeSize(request));
    if (emitOldMessagingSemconv()) {
      attributes.put(MESSAGING_CLIENT_ID_OLD, getter.getClientId(request));
    }
    if (emitStableMessagingSemconv()) {
      attributes.put(MESSAGING_CLIENT_ID, getter.getClientId(request));
    }
    if (emitOldMessagingSemconv() && operationType != null) {
      attributes.put(MESSAGING_OPERATION, operationType.defaultOperationName());
    }
    if (emitStableMessagingSemconv()) {
      attributes.put(MESSAGING_OPERATION_NAME, operationName);
      if (operationType != null) {
        attributes.put(MESSAGING_OPERATION_TYPE, operationType.value());
      }
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    attributes.put(MESSAGING_MESSAGE_ID, getter.getMessageId(request, response));
    attributes.put(MESSAGING_BATCH_MESSAGE_COUNT, getter.getBatchMessageCount(request, response));
    if (emitStableMessagingSemconv()) {
      String errorType = getter.getErrorType(request, response, error);
      if (errorType == null && error != null) {
        errorType = error.getClass().getName();
      }
      attributes.put(ERROR_TYPE, errorType);
    }

    for (String name : capturedHeaders) {
      List<String> values = getter.getMessageHeader(request, name);
      if (!values.isEmpty()) {
        attributes.put(CapturedMessageHeadersUtil.attributeKey(name), values);
      }
    }
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  @Nullable
  public SpanKey internalGetSpanKey() {
    if (operationType == null) {
      return null;
    }

    switch (operationType) {
      case CREATE:
        return SpanKey.PRODUCER_CREATE;
      case SEND:
        return SpanKey.PRODUCER;
      case RECEIVE:
        return SpanKey.CONSUMER_RECEIVE;
      case PROCESS:
        return SpanKey.CONSUMER_PROCESS;
      case SETTLE:
        return SpanKey.CONSUMER_SETTLE;
    }
    throw new IllegalStateException("Can't possibly happen");
  }
}
