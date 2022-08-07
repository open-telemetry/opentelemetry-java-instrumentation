/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

enum RabbitReceiveAttributesGetter
    implements MessagingAttributesGetter<ReceiveRequest, GetResponse> {
  INSTANCE;

  @Override
  public String system(ReceiveRequest request) {
    return "rabbitmq";
  }

  @Override
  public String destinationKind(ReceiveRequest request) {
    return SemanticAttributes.MessagingDestinationKindValues.QUEUE;
  }

  @Nullable
  @Override
  public String destination(ReceiveRequest request) {
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
  public boolean temporaryDestination(ReceiveRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String protocol(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String protocolVersion(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String url(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String conversationId(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadSize(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long messagePayloadCompressedSize(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String messageId(ReceiveRequest request, @Nullable GetResponse response) {
    return null;
  }
}
