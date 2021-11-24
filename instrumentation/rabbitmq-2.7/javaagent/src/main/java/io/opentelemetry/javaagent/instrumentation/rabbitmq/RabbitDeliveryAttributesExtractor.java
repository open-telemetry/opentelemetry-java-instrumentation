/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessageOperation;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public class RabbitDeliveryAttributesExtractor
    extends MessagingAttributesExtractor<DeliveryRequest, Void> {
  @Override
  public MessageOperation operation() {
    return MessageOperation.PROCESS;
  }

  @Override
  protected String system(DeliveryRequest request) {
    return "rabbitmq";
  }

  @Override
  protected String destinationKind(DeliveryRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.QUEUE;
  }

  @Nullable
  @Override
  protected String destination(DeliveryRequest request) {
    if (request.getEnvelope() != null) {
      return normalizeExchangeName(request.getEnvelope().getExchange());
    } else {
      return null;
    }
  }

  private static String normalizeExchangeName(String exchange) {
    return exchange == null || exchange.isEmpty() ? "<default>" : exchange;
  }

  @Override
  protected boolean temporaryDestination(DeliveryRequest request) {
    return false;
  }

  @Nullable
  @Override
  protected String protocol(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String protocolVersion(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String url(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String conversationId(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadSize(DeliveryRequest request) {
    if (request.getBody() != null) {
      return (long) request.getBody().length;
    }
    return null;
  }

  @Nullable
  @Override
  protected Long messagePayloadCompressedSize(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  protected String messageId(DeliveryRequest request, @Nullable Void unused) {
    return null;
  }
}
