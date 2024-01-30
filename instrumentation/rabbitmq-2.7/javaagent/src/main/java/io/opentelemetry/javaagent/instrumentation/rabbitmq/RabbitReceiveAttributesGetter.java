/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

enum RabbitReceiveAttributesGetter
    implements MessagingAttributesGetter<ReceiveRequest, GetResponse> {
  INSTANCE;

  @Override
  public String getSystem(ReceiveRequest request) {
    return "rabbitmq";
  }

  @Nullable
  @Override
  public String getDestination(ReceiveRequest request) {
    if (request.getResponse() != null) {
      return normalizeExchangeName(request.getResponse().getEnvelope().getExchange());
    } else {
      return null;
    }
  }

  @Nullable
  @Override
  public String getDestinationTemplate(ReceiveRequest request) {
    return null;
  }

  private static String normalizeExchangeName(String exchange) {
    return exchange == null || exchange.isEmpty() ? "<default>" : exchange;
  }

  @Override
  public boolean isTemporaryDestination(ReceiveRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(ReceiveRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(ReceiveRequest request, @Nullable GetResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(ReceiveRequest request, @Nullable GetResponse response) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(ReceiveRequest request, String name) {
    GetResponse response = request.getResponse();
    if (response != null) {
      Object value = request.getResponse().getProps().getHeaders().get(name);
      if (value != null) {
        return Collections.singletonList(value.toString());
      }
    }
    return Collections.emptyList();
  }
}
