/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
  private static final AttributeKey<String> MESSAGING_CLIENT_ID =
      AttributeKey.stringKey("messaging.client.id");
  private static final AttributeKey<Boolean> MESSAGING_DESTINATION_ANONYMOUS =
      AttributeKey.booleanKey("messaging.destination.anonymous");
  private static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      AttributeKey.stringKey("messaging.destination.name");
  private static final AttributeKey<String> MESSAGING_DESTINATION_PARTITION_ID =
      AttributeKey.stringKey("messaging.destination.partition.id");
  private static final AttributeKey<String> MESSAGING_DESTINATION_SUBSCRIPTION_NAME =
      AttributeKey.stringKey("messaging.destination.subscription.name");
  private static final AttributeKey<String> MESSAGING_DESTINATION_TEMPLATE =
      AttributeKey.stringKey("messaging.destination.template");
  private static final AttributeKey<Boolean> MESSAGING_DESTINATION_TEMPORARY =
      AttributeKey.booleanKey("messaging.destination.temporary");
  private static final AttributeKey<String> MESSAGING_MESSAGE_CONVERSATION_ID =
      AttributeKey.stringKey("messaging.message.conversation_id");
  private static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      AttributeKey.stringKey("messaging.message.id");
  private static final AttributeKey<String> MESSAGING_OPERATION_NAME =
      AttributeKey.stringKey("messaging.operation.name");
  private static final AttributeKey<String> MESSAGING_OPERATION_TYPE =
      AttributeKey.stringKey("messaging.operation.type");
  private static final AttributeKey<String> MESSAGING_SYSTEM =
      AttributeKey.stringKey("messaging.system");

  /**
   * Creates the messaging attributes extractor for the given operation type.
   *
   * @param operationName the system-specific name of the operation, emitted as {@code
   *     messaging.operation.name}, e.g. {@code send}, {@code poll} or {@code ack}.
   */
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      MessagingOperationType operationType,
      String operationName) {
    return builder(getter, operationType, operationName).build();
  }

  /**
   * Returns a new {@link MessagingAttributesExtractorBuilder} configured for the given operation
   * type.
   *
   * @param operationName the system-specific name of the operation, emitted as {@code
   *     messaging.operation.name}, e.g. {@code send}, {@code poll} or {@code ack}.
   */
  public static <REQUEST, RESPONSE> MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      MessagingOperationType operationType,
      String operationName) {
    return new MessagingAttributesExtractorBuilder<>(
        getter,
        requireNonNull(operationType, "operationType"),
        requireNonNull(operationName, "operationName"));
  }

  private final MessagingAttributesGetter<REQUEST, RESPONSE> getter;
  private final MessagingOperationType operationType;
  private final String operationName;
  private final Map<String, AttributeKey<List<String>>> capturedHeaders;

  MessagingAttributesExtractor(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      MessagingOperationType operationType,
      String operationName,
      Collection<String> capturedHeaders) {
    this.getter = getter;
    this.operationType = operationType;
    this.operationName = operationName;
    this.capturedHeaders = new LinkedHashMap<>();
    for (String name : capturedHeaders) {
      this.capturedHeaders.put(name, AttributeKey.stringArrayKey("messaging.header." + name));
    }
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    attributes.put(MESSAGING_SYSTEM, getter.getSystem(request));
    boolean isTemporaryDestination = getter.isTemporaryDestination(request);
    if (isTemporaryDestination) {
      attributes.put(MESSAGING_DESTINATION_TEMPORARY, true);
    }
    attributes.put(MESSAGING_DESTINATION_NAME, getter.getDestination(request));
    attributes.put(MESSAGING_DESTINATION_TEMPLATE, getter.getDestinationTemplate(request));
    attributes.put(MESSAGING_DESTINATION_PARTITION_ID, getter.getDestinationPartitionId(request));
    boolean isAnonymousDestination = getter.isAnonymousDestination(request);
    if (isAnonymousDestination) {
      attributes.put(MESSAGING_DESTINATION_ANONYMOUS, true);
    }
    attributes.put(MESSAGING_MESSAGE_CONVERSATION_ID, getter.getConversationId(request));
    attributes.put(MESSAGING_CLIENT_ID, getter.getClientId(request));
    attributes.put(
        MESSAGING_DESTINATION_SUBSCRIPTION_NAME, getter.getDestinationSubscriptionName(request));
    attributes.put(MESSAGING_OPERATION_NAME, operationName);
    attributes.put(MESSAGING_OPERATION_TYPE, operationType.value());
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
    attributes.put(ERROR_TYPE, getter.getErrorType(request, response, error));

    for (Map.Entry<String, AttributeKey<List<String>>> header : capturedHeaders.entrySet()) {
      List<String> values = getter.getMessageHeader(request, header.getKey());
      if (!values.isEmpty()) {
        attributes.put(header.getValue(), values);
      }
    }
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  public SpanKey internalGetSpanKey() {
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
