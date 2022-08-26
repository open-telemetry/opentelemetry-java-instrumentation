/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import static io.opentelemetry.instrumentation.api.instrumenter.messaging.CapturedMessageHeadersUtil.attributeKey;
import static io.opentelemetry.instrumentation.api.instrumenter.messaging.CapturedMessageHeadersUtil.lowercase;
import static io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation.PROCESS;
import static io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation.RECEIVE;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.internalSet;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Extractor of <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/messaging.md">messaging
 * attributes</a>.
 *
 * <p>This class delegates to a type-specific {@link MessagingAttributesGetter} for individual
 * attribute extraction from request/response objects.
 */
public final class MessagingAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE>, SpanKeyProvider {

  static final String TEMP_DESTINATION_NAME = "(temporary)";

  /**
   * Creates the messaging attributes extractor for the given {@link MessageOperation operation}
   * with default configuration.
   */
  public static <REQUEST, RESPONSE> MessagingAttributesExtractor<REQUEST, RESPONSE> create(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter, MessageOperation operation) {
    return builder(getter, operation).build();
  }

  /**
   * Returns a new {@link MessagingAttributesExtractorBuilder} for the given {@link MessageOperation
   * operation} that can be used to configure the messaging attributes extractor.
   */
  public static <REQUEST, RESPONSE> MessagingAttributesExtractorBuilder<REQUEST, RESPONSE> builder(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter, MessageOperation operation) {
    return new MessagingAttributesExtractorBuilder<>(getter, operation);
  }

  private final MessagingAttributesGetter<REQUEST, RESPONSE> getter;
  private final MessageOperation operation;
  private final List<String> capturedHeaders;

  MessagingAttributesExtractor(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter,
      MessageOperation operation,
      List<String> capturedHeaders) {
    this.getter = getter;
    this.operation = operation;
    this.capturedHeaders = lowercase(capturedHeaders);
  }

  @SuppressWarnings("deprecation") // operationName
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    internalSet(attributes, SemanticAttributes.MESSAGING_SYSTEM, getter.system(request));
    internalSet(
        attributes, SemanticAttributes.MESSAGING_DESTINATION_KIND, getter.destinationKind(request));
    boolean isTemporaryDestination = getter.temporaryDestination(request);
    if (isTemporaryDestination) {
      internalSet(attributes, SemanticAttributes.MESSAGING_TEMP_DESTINATION, true);
      internalSet(attributes, SemanticAttributes.MESSAGING_DESTINATION, TEMP_DESTINATION_NAME);
    } else {
      internalSet(
          attributes, SemanticAttributes.MESSAGING_DESTINATION, getter.destination(request));
    }
    internalSet(attributes, SemanticAttributes.MESSAGING_PROTOCOL, getter.protocol(request));
    internalSet(
        attributes, SemanticAttributes.MESSAGING_PROTOCOL_VERSION, getter.protocolVersion(request));
    internalSet(attributes, SemanticAttributes.MESSAGING_URL, getter.url(request));
    internalSet(
        attributes, SemanticAttributes.MESSAGING_CONVERSATION_ID, getter.conversationId(request));
    internalSet(
        attributes,
        SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
        getter.messagePayloadSize(request));
    internalSet(
        attributes,
        SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_COMPRESSED_SIZE_BYTES,
        getter.messagePayloadCompressedSize(request));
    if (operation == RECEIVE || operation == PROCESS) {
      internalSet(attributes, SemanticAttributes.MESSAGING_OPERATION, operation.operationName());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    internalSet(
        attributes, SemanticAttributes.MESSAGING_MESSAGE_ID, getter.messageId(request, response));

    for (String name : capturedHeaders) {
      List<String> values = getter.header(request, name);
      if (!values.isEmpty()) {
        internalSet(attributes, attributeKey(name), values);
      }
    }
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @Override
  public SpanKey internalGetSpanKey() {
    switch (operation) {
      case SEND:
        return SpanKey.PRODUCER;
      case RECEIVE:
        return SpanKey.CONSUMER_RECEIVE;
      case PROCESS:
        return SpanKey.CONSUMER_PROCESS;
    }
    throw new IllegalStateException("Can't possibly happen");
  }
}
