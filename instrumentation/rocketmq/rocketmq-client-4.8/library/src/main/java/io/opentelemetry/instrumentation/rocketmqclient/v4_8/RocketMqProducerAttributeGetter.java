/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    if (message == null) {
      return null;
    }
    if (!emitStableMessagingSemconv()) {
      return message.getTopic();
    }
    return RocketMqNamespaceUtil.withoutNamespace(
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
    // the send result of a batch carries the concatenated ids of every message it contains, which
    // is not a per-message id, so it is not reported
    if (isBatch(request)) {
      return null;
    }
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
    if (!isBatch(request)) {
      return null;
    }
    long batchSize = 0;
    for (Object ignored : (Iterable<?>) request.getMessage()) {
      batchSize++;
    }
    return batchSize;
  }

  private static boolean isBatch(SendMessageContext request) {
    return emitStableMessagingSemconv() && request.getMessage() instanceof Iterable<?>;
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

  @Override
  public Collection<String> getMessageHeaderNames(SendMessageContext request) {
    Message message = request.getMessage();
    if (message == null) {
      return emptyList();
    }
    Map<String, String> properties = message.getProperties();
    return properties == null ? emptyList() : new ArrayList<>(properties.keySet());
  }
}
