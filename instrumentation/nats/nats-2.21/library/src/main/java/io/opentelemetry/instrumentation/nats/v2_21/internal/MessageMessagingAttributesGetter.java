/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.nats.v2_21.internal;

import io.nats.client.Message;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;

enum MessageMessagingAttributesGetter implements MessagingAttributesGetter<Message, Void> {
  INSTANCE;

  @Nullable
  @Override
  public String getSystem(Message message) {
    return "nats";
  }

  @Nullable
  @Override
  public String getDestination(Message message) {
    return message.getSubject();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(Message message) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(Message message) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(Message message) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(Message message) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(Message message) {
    return (long) message.getData().length;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(Message message) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(Message message, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(Message message) {
    return String.valueOf(message.getConnection().getServerInfo().getClientId());
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(Message message, @Nullable Void unused) {
    return null;
  }

  @Override
  public List<String> getMessageHeader(Message message, String name) {
    return message.getHeaders().get(name);
  }
}
