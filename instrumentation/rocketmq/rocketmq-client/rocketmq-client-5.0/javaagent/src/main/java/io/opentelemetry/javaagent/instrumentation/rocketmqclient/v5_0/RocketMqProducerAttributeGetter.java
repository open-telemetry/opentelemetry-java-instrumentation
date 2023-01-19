/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.java.impl.producer.SendReceiptImpl;
import org.apache.rocketmq.client.java.message.PublishingMessageImpl;

enum RocketMqProducerAttributeGetter
    implements MessagingAttributesGetter<PublishingMessageImpl, SendReceiptImpl> {
  INSTANCE;

  @Nullable
  @Override
  public String getSystem(PublishingMessageImpl message) {
    return "rocketmq";
  }

  @Nullable
  @Override
  public String getDestinationKind(PublishingMessageImpl message) {
    return SemanticAttributes.MessagingDestinationKindValues.TOPIC;
  }

  @Nullable
  @Override
  public String getDestination(PublishingMessageImpl message) {
    return message.getTopic();
  }

  @Override
  public boolean isTemporaryDestination(PublishingMessageImpl message) {
    return false;
  }

  @Nullable
  @Override
  public String getProtocol(PublishingMessageImpl message) {
    return null;
  }

  @Nullable
  @Override
  public String getProtocolVersion(PublishingMessageImpl message) {
    return null;
  }

  @Nullable
  @Override
  public String getUrl(PublishingMessageImpl message) {
    return null;
  }

  @Nullable
  @Override
  public String getConversationId(PublishingMessageImpl message) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessagePayloadSize(PublishingMessageImpl message) {
    return (long) message.getBody().remaining();
  }

  @Nullable
  @Override
  public Long getMessagePayloadCompressedSize(PublishingMessageImpl message) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(PublishingMessageImpl message, @Nullable SendReceiptImpl sendReceipt) {
    return message.getMessageId().toString();
  }

  @Override
  public List<String> getMessageHeader(PublishingMessageImpl message, String name) {
    String value = message.getProperties().get(name);
    if (value != null) {
      return Collections.singletonList(value);
    }
    return Collections.emptyList();
  }
}
