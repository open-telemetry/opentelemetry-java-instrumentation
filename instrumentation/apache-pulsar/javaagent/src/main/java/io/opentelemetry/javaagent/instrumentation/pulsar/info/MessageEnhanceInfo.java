/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.info;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageId;
import org.apache.pulsar.client.impl.MessageIdImpl;

public class MessageEnhanceInfo {
  private static final VirtualField<Message<?>, MessageEnhanceInfo> MESSAGE_ENHANCED_FIELD =
      VirtualField.find(Message.class, MessageEnhanceInfo.class);

  private String topic;
  private String messageId;
  private Context context;

  public MessageEnhanceInfo() {}

  public Context getContext() {
    return context;
  }

  public String getTopic() {
    return topic;
  }

  public String getMessageId() {
    return messageId;
  }

  public void setFields(Context context, String topic, MessageId messageId) {
    this.context = context;
    this.topic = topic;
    if (messageId instanceof MessageIdImpl) {
      MessageIdImpl impl = (MessageIdImpl) messageId;
      this.messageId = impl.getLedgerId() + ":" + impl.getEntryId();
    } else {
      this.messageId = "unknown";
    }
  }

  public static void setMessageEnhancedField(Message<?> message, MessageEnhanceInfo context) {
    MESSAGE_ENHANCED_FIELD.set(message, context);
  }

  public static MessageEnhanceInfo getMessageEnhancedField(Message<?> message) {
    return MESSAGE_ENHANCED_FIELD.get(message);
  }
}
