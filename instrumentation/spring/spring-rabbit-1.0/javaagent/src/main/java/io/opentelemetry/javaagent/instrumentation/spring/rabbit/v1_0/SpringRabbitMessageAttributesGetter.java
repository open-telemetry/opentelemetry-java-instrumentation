/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.messaging.MessagingAttributesGetter;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

enum SpringRabbitMessageAttributesGetter implements MessagingAttributesGetter<Message, Void> {
  INSTANCE;

  @Override
  public String getSystem(Message message) {
    return "rabbitmq";
  }

  @Override
  public String getDestinationKind(Message message) {
    return "queue";
  }

  @Override
  @Nullable
  public String getDestination(Message message) {
    return message.getMessageProperties().getReceivedRoutingKey();
  }

  @Override
  public boolean isTemporaryDestination(Message message) {
    return false;
  }

  @Override
  @Nullable
  public String getProtocol(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String getProtocolVersion(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String getUrl(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String getConversationId(Message message) {
    return null;
  }

  @Override
  public Long getMessagePayloadSize(Message message) {
    return message.getMessageProperties().getContentLength();
  }

  @Override
  @Nullable
  public Long getMessagePayloadCompressedSize(Message message) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(Message message, @Nullable Void unused) {
    return message.getMessageProperties().getMessageId();
  }

  @Override
  public List<String> getMessageHeader(Message message, String name) {
    Object value = message.getMessageProperties().getHeaders().get(name);
    if (value != null) {
      return Collections.singletonList(value.toString());
    }
    return Collections.emptyList();
  }
}
