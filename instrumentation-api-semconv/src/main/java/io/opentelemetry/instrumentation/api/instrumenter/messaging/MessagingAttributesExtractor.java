/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter.messaging;

import static io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation.PROCESS;
import static io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation.RECEIVE;
import static io.opentelemetry.instrumentation.api.internal.AttributesExtractorUtil.setAttr;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotations.UnstableApi;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.instrumentation.api.internal.SpanKeyProvider;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
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
   * Creates the messaging attributes extractor for the given {@link MessageOperation operation}.
   */
  public static <REQUEST, RESPONSE> MessagingAttributesExtractor<REQUEST, RESPONSE> create(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter, MessageOperation operation) {
    return new MessagingAttributesExtractor<>(getter, operation);
  }

  private final MessagingAttributesGetter<REQUEST, RESPONSE> getter;
  private final MessageOperation operation;

  private MessagingAttributesExtractor(
      MessagingAttributesGetter<REQUEST, RESPONSE> getter, MessageOperation operation) {
    this.getter = getter;
    this.operation = operation;
  }

  @SuppressWarnings("deprecation") // operationName
  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    setAttr(attributes, SemanticAttributes.MESSAGING_SYSTEM, getter.system(request));
    setAttr(
        attributes, SemanticAttributes.MESSAGING_DESTINATION_KIND, getter.destinationKind(request));
    boolean isTemporaryDestination = getter.temporaryDestination(request);
    if (isTemporaryDestination) {
      setAttr(attributes, SemanticAttributes.MESSAGING_TEMP_DESTINATION, true);
      setAttr(attributes, SemanticAttributes.MESSAGING_DESTINATION, TEMP_DESTINATION_NAME);
    } else {
      setAttr(attributes, SemanticAttributes.MESSAGING_DESTINATION, getter.destination(request));
    }
    setAttr(attributes, SemanticAttributes.MESSAGING_PROTOCOL, getter.protocol(request));
    setAttr(
        attributes, SemanticAttributes.MESSAGING_PROTOCOL_VERSION, getter.protocolVersion(request));
    setAttr(attributes, SemanticAttributes.MESSAGING_URL, getter.url(request));
    setAttr(
        attributes, SemanticAttributes.MESSAGING_CONVERSATION_ID, getter.conversationId(request));
    setAttr(
        attributes,
        SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_SIZE_BYTES,
        getter.messagePayloadSize(request));
    setAttr(
        attributes,
        SemanticAttributes.MESSAGING_MESSAGE_PAYLOAD_COMPRESSED_SIZE_BYTES,
        getter.messagePayloadCompressedSize(request));
    if (operation == RECEIVE || operation == PROCESS) {
      setAttr(attributes, SemanticAttributes.MESSAGING_OPERATION, operation.operationName());
    }
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {
    setAttr(
        attributes, SemanticAttributes.MESSAGING_MESSAGE_ID, getter.messageId(request, response));
  }

  /**
   * This method is internal and is hence not for public use. Its API is unstable and can change at
   * any time.
   */
  @UnstableApi
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
