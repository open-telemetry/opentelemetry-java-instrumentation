/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import apache.rocketmq.v2.ReceiveMessageRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqReceiveRequest {

  private final ReceiveMessageRequest request;
  private final List<MessageView> messages;
  private final String destination;
  private final String messageId;
  private final String messageTag;
  private final String messageGroup;
  private final Long messageDeliveryTimestamp;
  private final List<String> messageKeys;

  private RocketMqReceiveRequest(ReceiveMessageRequest request, List<MessageView> messages) {
    this.request = request;
    this.messages = messages;
    this.destination =
        messages.isEmpty()
            ? request.getMessageQueue().getTopic().getName()
            : commonValue(messages, MessageView::getTopic);
    this.messageId =
        commonValue(messages, message -> Objects.toString(message.getMessageId(), null));
    this.messageTag = commonValue(messages, message -> message.getTag().orElse(null));
    this.messageGroup = commonValue(messages, message -> message.getMessageGroup().orElse(null));
    this.messageDeliveryTimestamp =
        commonValue(messages, message -> message.getDeliveryTimestamp().orElse(null));
    this.messageKeys = commonKeys(messages);
  }

  static RocketMqReceiveRequest create(ReceiveMessageRequest request, List<MessageView> messages) {
    return new RocketMqReceiveRequest(request, messages);
  }

  ReceiveMessageRequest getRequest() {
    return request;
  }

  List<MessageView> getMessages() {
    return messages;
  }

  @Nullable
  String getDestination() {
    return destination;
  }

  @Nullable
  String getMessageId() {
    return messageId;
  }

  @Nullable
  String getMessageTag() {
    return messageTag;
  }

  @Nullable
  String getMessageGroup() {
    return messageGroup;
  }

  @Nullable
  Long getMessageDeliveryTimestamp() {
    return messageDeliveryTimestamp;
  }

  @Nullable
  List<String> getMessageKeys() {
    return messageKeys;
  }

  @Nullable
  private static <T> T commonValue(
      List<MessageView> messages, Function<MessageView, T> valueExtractor) {
    if (messages.isEmpty()) {
      return null;
    }
    T value = valueExtractor.apply(messages.get(0));
    for (int i = 1; i < messages.size(); i++) {
      if (!Objects.equals(value, valueExtractor.apply(messages.get(i)))) {
        return null;
      }
    }
    return value;
  }

  @Nullable
  private static List<String> commonKeys(List<MessageView> messages) {
    if (messages.isEmpty()) {
      return null;
    }
    List<String> keys = new ArrayList<>(messages.get(0).getKeys());
    HashSet<String> keySet = new HashSet<>(keys);
    for (int i = 1; i < messages.size(); i++) {
      if (!keySet.equals(new HashSet<>(messages.get(i).getKeys()))) {
        return null;
      }
    }
    return keys;
  }
}
