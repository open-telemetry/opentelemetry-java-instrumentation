/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import javax.annotation.Nullable;

class RocketMqAckAttributeGetter implements MessagingAttributesGetter<RocketMqAckRequest, Void> {

  @Override
  public String getSystem(RocketMqAckRequest request) {
    return "rocketmq";
  }

  @Override
  public String getDestination(RocketMqAckRequest request) {
    return request.getMessage().getTopic();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(RocketMqAckRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(RocketMqAckRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(RocketMqAckRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(RocketMqAckRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(RocketMqAckRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(RocketMqAckRequest request) {
    return null;
  }

  @Override
  public String getMessageId(RocketMqAckRequest request, @Nullable Void unused) {
    return request.getMessage().getMessageId().toString();
  }

  @Nullable
  @Override
  public String getClientId(RocketMqAckRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(RocketMqAckRequest request, @Nullable Void unused) {
    return null;
  }
}
