/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerAttributeGetter
    implements MessagingAttributesGetter<RocketMqConsumerRequest, ConsumeMessageContext> {

  @Override
  public String getSystem(RocketMqConsumerRequest request) {
    return "rocketmq";
  }

  @Override
  public String getDestination(RocketMqConsumerRequest request) {
    return request.getMessage().getTopic();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(RocketMqConsumerRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(RocketMqConsumerRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(RocketMqConsumerRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(RocketMqConsumerRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(RocketMqConsumerRequest request) {
    if (request.isBatch()) {
      // per-message attributes that vary across a batch belong on the span links
      return null;
    }
    byte[] body = request.getMessage().getBody();
    return body == null ? null : (long) body.length;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(RocketMqConsumerRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(
      RocketMqConsumerRequest request, @Nullable ConsumeMessageContext unused) {
    return request.isBatch() ? null : request.getMessage().getMsgId();
  }

  @Nullable
  @Override
  public String getClientId(RocketMqConsumerRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(
      RocketMqConsumerRequest request, @Nullable ConsumeMessageContext unused) {
    return request.isBatch() ? (long) request.getBatchSize() : null;
  }

  @Nullable
  @Override
  public String getErrorType(
      RocketMqConsumerRequest request,
      @Nullable ConsumeMessageContext response,
      @Nullable Throwable error) {
    if (response == null || response.isSuccess()) {
      return null;
    }
    // rocketmq coerces a null consume status to RECONSUME_LATER before invoking the hook, so the
    // status alone cannot tell a listener that threw or returned null apart from one that asked
    // for redelivery; the consume return type keeps that distinction
    Map<String, String> props = response.getProps();
    String consumeReturnType = props == null ? null : props.get(MixAll.CONSUME_CONTEXT_TYPE);
    return consumeReturnType != null ? consumeReturnType : response.getStatus();
  }

  @Override
  public List<String> getMessageHeader(RocketMqConsumerRequest request, String name) {
    List<String> values = new ArrayList<>();
    for (MessageExt message : request.getMessages()) {
      String value = message.getProperties().get(name);
      if (value != null) {
        values.add(value);
      }
    }
    return values;
  }
}
