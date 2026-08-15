/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.messaging;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private static final AttributeKey<String> MESSAGING_DESTINATION_SUBSCRIPTION_NAME =
      AttributeKey.stringKey("messaging.destination.subscription.name");
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
   * @deprecated Use {@link #create(MessagingAttributesGetter, MessagingOperationType, String)}. May
   *     be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public static <REQUEST, RESPONSE> AttributesExtractor<REQUEST, RESPONSE> create(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter, @Nullable MessageOperation operation) {
    return builder(getter, operation).build();
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
        getter, operationType, requireNonNull(operationName, "operationName"), true);
  }

  /**
   * @deprecated Use {@link #builder(MessagingAttributesGetter, MessagingOperationType, String)}.
   *     May be removed in the next minor release.
   */
  @Deprecated // may be removed in the next minor release
  public static <REQUEST, RESPONSE> MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter, @Nullable MessageOperation operation) {
    return new MessagingAttributesExtractorBuilder<>(
        getter, operation == null ? null : operation.type(), null, false);
  }

  private final MessagingAttributesGetter<REQUEST, RESPONSE> getter;
  @Nullable private final MessagingOperationType operationType;
  @Nullable private final String operationName;
  private final boolean supportsStableSemconv;
  @Nullable private final IncludeExclude headers;
  // exact header names that the selector includes, queried directly so that getters which only
  // implement getMessageHeader() keep working
  private final List<String> exactHeaderNames;
  private final Map<String, AttributeKey<List<String>>> exactHeaderAttributeKeys;
  // whether the selector can match header names that are not listed in exactHeaderNames, which
  // requires enumerating the header names of each message
  private final boolean enumerateHeaderNames;

  MessagingAttributesExtractor(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      @Nullable MessagingOperationType operationType,
      @Nullable String operationName,
      boolean supportsStableSemconv,
      @Nullable IncludeExclude headers) {
    this.getter = getter;
    this.operationType = operationType;
    this.operationName = operationName;
    this.supportsStableSemconv = supportsStableSemconv;
    this.headers = headers;

    Set<String> exactNames = new LinkedHashSet<>();
    boolean enumerate = false;
    if (headers != null) {
      List<String> included = headers.getIncluded();
      // a selector without included patterns matches every header name that is not excluded
      enumerate = included.isEmpty();
      for (String pattern : included) {
        if (pattern.indexOf('*') != -1 || pattern.indexOf('?') != -1) {
          enumerate = true;
        } else if (headers.matches(pattern)) {
          exactNames.add(pattern);
        }
      }
    }
    this.exactHeaderNames = unmodifiableList(new ArrayList<>(exactNames));
    this.exactHeaderAttributeKeys =
        CapturedMessageHeadersUtil.createLiteralAttributeKeys(exactHeaderNames);
    this.enumerateHeaderNames = enumerate;
  }

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    boolean emitOldSemconv = !supportsStableSemconv || emitOldMessagingSemconv();
    boolean emitStableSemconv = supportsStableSemconv && emitStableMessagingSemconv();
    attributes.put(MESSAGING_SYSTEM, getter.getSystem(request));
    boolean isTemporaryDestination = getter.isTemporaryDestination(request);
    if (isTemporaryDestination) {
      attributes.put(MESSAGING_DESTINATION_TEMPORARY, true);
      if (emitStableSemconv) {
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
    if (emitOldSemconv) {
      // the message size attributes are opt-in in the v1.43 messaging semantic conventions
      attributes.put(MESSAGING_MESSAGE_BODY_SIZE, getter.getMessageBodySize(request));
      attributes.put(MESSAGING_MESSAGE_ENVELOPE_SIZE, getter.getMessageEnvelopeSize(request));
      attributes.put(MESSAGING_CLIENT_ID_OLD, getter.getClientId(request));
    }
    if (emitStableSemconv) {
      attributes.put(MESSAGING_CLIENT_ID, getter.getClientId(request));
      // messaging.destination.subscription.name only exists in the v1.43 messaging semantic
      // conventions
      attributes.put(
          MESSAGING_DESTINATION_SUBSCRIPTION_NAME, getter.getDestinationSubscriptionName(request));
    }
    if (emitOldSemconv && operationType != null) {
      attributes.put(MESSAGING_OPERATION, operationType.legacyOperationName());
    }
    if (emitStableSemconv) {
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
    if (supportsStableSemconv && emitStableMessagingSemconv()) {
      String errorType = getter.getErrorType(request, response, error);
      if (errorType == null && error != null) {
        errorType = error.getClass().getName();
      }
      attributes.put(ERROR_TYPE, errorType);
    }

    for (String name : headerNames(request)) {
      List<String> values = getter.getMessageHeader(request, name);
      if (!values.isEmpty()) {
        attributes.put(
            CapturedMessageHeadersUtil.attributeKey(name, exactHeaderAttributeKeys), values);
      }
    }
  }

  private Collection<String> headerNames(REQUEST request) {
    if (headers == null) {
      return emptyList();
    }
    if (!enumerateHeaderNames) {
      return exactHeaderNames;
    }
    Set<String> names = new LinkedHashSet<>(exactHeaderNames);
    for (String name : getter.getMessageHeaderNames(request)) {
      if (headers.matches(name)) {
        names.add(name);
      }
    }
    return names;
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
