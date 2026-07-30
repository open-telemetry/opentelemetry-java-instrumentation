/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.hook.ConsumeMessageContext;

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
    return request.getMessage().getMsgId();
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
    return null;
  }

  @Nullable
  @Override
  public String getErrorType(
      RocketMqConsumerRequest request,
      @Nullable ConsumeMessageContext response,
      @Nullable Throwable error) {
    return response != null && !response.isSuccess() ? response.getStatus() : null;
  }

  @Override
  public List<String> getMessageHeader(RocketMqConsumerRequest request, String name) {
    String value = request.getMessage().getProperties().get(name);
    if (value != null) {
      return singletonList(value);
    }
    return emptyList();
  }
}
