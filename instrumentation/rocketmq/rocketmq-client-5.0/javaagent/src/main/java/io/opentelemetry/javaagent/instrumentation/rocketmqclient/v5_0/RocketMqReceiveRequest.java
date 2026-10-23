/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import apache.rocketmq.v2.ReceiveMessageRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.message.MessageViewImpl;

class RocketMqReceiveRequest {

  @Nullable private final ReceiveMessageRequest request;
  @Nullable private final String destination;
  @Nullable private final String namespace;
  @Nullable private final String consumerGroup;
  private final List<MessageView> messages;
  @Nullable private BatchMessageAttributes batchMessageAttributes;

  private RocketMqReceiveRequest(ReceiveMessageRequest request, List<MessageView> messages) {
    this.request = request;
    this.destination = null;
    this.namespace = null;
    this.consumerGroup = null;
    this.messages = messages;
  }

  private RocketMqReceiveRequest(
      @Nullable String destination,
      @Nullable String namespace,
      String consumerGroup,
      List<MessageView> messages) {
    this.request = null;
    this.destination = destination;
    this.namespace = namespace;
    this.consumerGroup = consumerGroup;
    this.messages = messages;
  }

  static RocketMqReceiveRequest create(ReceiveMessageRequest request, List<MessageView> messages) {
    return new RocketMqReceiveRequest(request, messages);
  }

  static RocketMqReceiveRequest create(String consumerGroup) {
    return new RocketMqReceiveRequest(null, null, consumerGroup, emptyList());
  }

  static RocketMqReceiveRequest create(String consumerGroup, List<MessageView> messages) {
    MessageView message = messages.get(0);
    String namespace =
        ((MessageViewImpl) message).getMessageQueue().getTopicResource().getNamespace();
    return new RocketMqReceiveRequest(message.getTopic(), namespace, consumerGroup, messages);
  }

  @Nullable
  String getRequestDestination() {
    ReceiveMessageRequest request = this.request;
    return request == null ? destination : request.getMessageQueue().getTopic().getName();
  }

  @Nullable
  String getNamespace() {
    ReceiveMessageRequest request = this.request;
    return request == null
        ? namespace
        : request.getMessageQueue().getTopic().getResourceNamespace();
  }

  String getConsumerGroup() {
    ReceiveMessageRequest request = this.request;
    return request == null ? requireNonNull(consumerGroup) : request.getGroup().getName();
  }

  List<MessageView> getMessages() {
    return messages;
  }

  @Nullable
  String getDestination() {
    return getBatchMessageAttributes().destination;
  }

  @Nullable
  String getMessageTag() {
    return getBatchMessageAttributes().messageTag;
  }

  @Nullable
  String getMessageGroup() {
    return getBatchMessageAttributes().messageGroup;
  }

  @Nullable
  Long getMessageDeliveryTimestamp() {
    return getBatchMessageAttributes().messageDeliveryTimestamp;
  }

  @Nullable
  List<String> getMessageKeys() {
    return getBatchMessageAttributes().messageKeys;
  }

  private BatchMessageAttributes getBatchMessageAttributes() {
    BatchMessageAttributes attributes = batchMessageAttributes;
    if (attributes == null) {
      attributes =
          new BatchMessageAttributes(messages.isEmpty() ? getRequestDestination() : null, messages);
      batchMessageAttributes = attributes;
    }
    return attributes;
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

  private static class BatchMessageAttributes {
    @Nullable private final String destination;
    @Nullable private final String messageTag;
    @Nullable private final String messageGroup;
    @Nullable private final Long messageDeliveryTimestamp;
    @Nullable private final List<String> messageKeys;

    private BatchMessageAttributes(
        @Nullable String requestDestination, List<MessageView> messages) {
      destination =
          messages.isEmpty() ? requestDestination : commonValue(messages, MessageView::getTopic);
      messageTag = commonValue(messages, message -> message.getTag().orElse(null));
      messageGroup = commonValue(messages, message -> message.getMessageGroup().orElse(null));
      messageDeliveryTimestamp =
          commonValue(messages, message -> message.getDeliveryTimestamp().orElse(null));
      messageKeys = commonKeys(messages);
    }
  }
}
