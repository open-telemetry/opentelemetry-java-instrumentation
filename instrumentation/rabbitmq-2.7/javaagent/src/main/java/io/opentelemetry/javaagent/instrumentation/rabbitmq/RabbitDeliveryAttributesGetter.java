/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

enum RabbitDeliveryAttributesGetter implements MessagingAttributesGetter<DeliveryRequest, Void> {
  INSTANCE;

  @Override
  public String system(DeliveryRequest request) {
    return "rabbitmq";
  }

  @Override
  public String destinationKind(DeliveryRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.QUEUE;
  }

  @Nullable
  @Override
  public String destination(DeliveryRequest request) {
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
  public boolean temporaryDestination(DeliveryRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String url(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(DeliveryRequest request) {
    if (request.getBody() != null) {
      return (long) request.getBody().length;
    }
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(DeliveryRequest request, @Nullable Void unused) {
    return null;
  }
}
