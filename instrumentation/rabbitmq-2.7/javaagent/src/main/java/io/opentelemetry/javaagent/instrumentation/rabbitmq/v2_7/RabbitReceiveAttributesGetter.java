/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitInstrumenterHelper.consumerDestinationName;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7.RabbitInstrumenterHelper.isGeneratedQueueName;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class RabbitReceiveAttributesGetter
    implements MessagingAttributesGetter<ReceiveRequest, GetResponse> {

  @Override
  public String getSystem(ReceiveRequest request) {
    return "rabbitmq";
  }

  @Nullable
  @Override
  public String getDestination(ReceiveRequest request) {
    GetResponse response = request.getResponse();
    if (emitStableMessagingSemconv()) {
      return consumerDestinationName(
          response == null ? null : response.getEnvelope().getExchange(),
          response == null ? null : response.getEnvelope().getRoutingKey(),
          request.getQueue());
    }
    if (response == null) {
      return null;
    }
    return normalizeExchangeName(response.getEnvelope().getExchange());
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
    return isGeneratedQueueName(request.getQueue());
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
    if (response == null) {
      return emptyList();
    }
    Map<String, Object> headers = response.getProps().getHeaders();
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
