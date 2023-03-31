/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

enum RabbitDeliveryAttributesGetter implements MessagingAttributesGetter<DeliveryRequest, Void> {
  INSTANCE;

  @Override
  public String getSystem(DeliveryRequest request) {
    return "rabbitmq";
  }

  @Override
  public String getDestinationKind(DeliveryRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.QUEUE;
  }

  @Nullable
  @Override
  public String getDestination(DeliveryRequest request) {
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
  public boolean isTemporaryDestination(DeliveryRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(DeliveryRequest request) {
    if (request.getBody() != null) {
      return (long) request.getBody().length;
    }
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(DeliveryRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(DeliveryRequest request, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(DeliveryRequest request, String name) {
    Map<String, Object> headers = request.getProperties().getHeaders();
    if (headers == null) {
      return emptyList();
    }
    Object value = headers.get(name);
    if (value == null) {
      return emptyList();
    }
    return singletonList(value.toString());
  }
}
