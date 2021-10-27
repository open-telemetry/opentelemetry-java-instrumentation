/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public class RabbitReceiveAttributesExtractor
    extends MessagingAttributesExtractor<ReceiveRequest, GetResponse> {
  @Override
  public MessageOperation operation() {
    return MessageOperation.RECEIVE;
  }

  @Override
  protected String system(ReceiveRequest request) {
    return "rabbitmq";
  }

  @Override
  protected String destinationKind(ReceiveRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.QUEUE;
  }

  @Nullable
  @Override
  protected String destination(ReceiveRequest request) {
    if (request.getResponse() != null) {
      return normalizeExchangeName(request.getResponse().getEnvelope().getExchange());
    } else {
      return null;
    }
  }

  private static String normalizeExchangeName(String exchange) {
    return exchange == null || exchange.isEmpty() ? "<default>" : exchange;
  }

  @Override
  protected boolean temporaryDestination(ReceiveRequest request) {
    return false;
  }

  @Nullable
  @Override
  protected String protocol(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String protocolVersion(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String url(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String conversationId(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadSize(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadCompressedSize(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String messageId(ReceiveRequest request, @Nullable GetResponse response) {
    return null;
  }
}
