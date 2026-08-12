/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static java.util.Collections.emptyList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqConsumerReceiveAttributeGetter
    implements MessagingAttributesGetter<RocketMqReceiveRequest, List<MessageView>> {

  @Override
  public String getSystem(RocketMqReceiveRequest request) {
    return "rocketmq";
  }

  @Override
  public String getDestination(RocketMqReceiveRequest request) {
    return request.getRequest().getMessageQueue().getTopic().getName();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(RocketMqReceiveRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(RocketMqReceiveRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(RocketMqReceiveRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(RocketMqReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(RocketMqReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(RocketMqReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(RocketMqReceiveRequest request, @Nullable List<MessageView> unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(RocketMqReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(
      RocketMqReceiveRequest request, @Nullable List<MessageView> messages) {
    return messages != null ? (long) messages.size() : null;
  }

  @Override
  public Collection<String> getMessageHeaderNames(RocketMqReceiveRequest request) {
    // per-message headers that vary across a batch belong on the span links, so the receive span
    // does not capture any headers
    return emptyList();
  }
}
