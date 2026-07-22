/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.SendMessageContext;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;

final class RocketMqProducerAttributeGetter
    implements MessagingAttributesGetter<SendMessageContext, Void> {

  @Override
  public String getSystem(SendMessageContext request) {
    return "rocketmq";
  }

  @Nullable
  @Override
  public String getDestination(SendMessageContext request) {
    Message message = request.getMessage();
    return message == null
        ? null
        : RocketMqNamespaceUtil.withoutNamespace(
            message.getTopic(), RocketMqNamespaceUtil.getNamespace(request));
  }

  @Nullable
  @Override
  public String getDestinationTemplate(SendMessageContext request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(SendMessageContext request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(SendMessageContext request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(SendMessageContext request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(SendMessageContext request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(SendMessageContext request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(SendMessageContext request, @Nullable Void unused) {
    SendResult sendResult = request.getSendResult();
    return sendResult == null ? null : sendResult.getMsgId();
  }

  @Nullable
  @Override
  public String getClientId(SendMessageContext request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(SendMessageContext request, @Nullable Void unused) {
    Message message = request.getMessage();
    if (emitStableMessagingSemconv() && message instanceof Iterable<?>) {
      long batchSize = 0;
      for (Object ignored : (Iterable<?>) message) {
        batchSize++;
      }
      return batchSize;
    }
    return null;
  }

  @Override
  public List<String> getMessageHeader(SendMessageContext request, String name) {
    Message message = request.getMessage();
    if (message == null) {
      return emptyList();
    }
    String value = message.getProperties().get(name);
    if (value != null) {
      return singletonList(value);
    }
    return emptyList();
  }
}
