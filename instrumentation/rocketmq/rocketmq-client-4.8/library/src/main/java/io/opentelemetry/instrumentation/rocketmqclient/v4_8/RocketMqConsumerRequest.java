/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Collections.singletonList;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerRequest {

  private final MessageExt message;
  private final List<MessageExt> messages;
  private final String consumerGroup;
  private final int batchSize;
  private final String namespace;
  private boolean destinationInitialized;
  @Nullable private String destination;
  private boolean messageIdInitialized;
  @Nullable private String messageId;
  private boolean messageTagInitialized;
  @Nullable private String messageTag;

  RocketMqConsumerRequest(
      MessageExt message, String consumerGroup, int batchSize, @Nullable String namespace) {
    this(message, singletonList(message), consumerGroup, batchSize, namespace);
  }

  RocketMqConsumerRequest(
      List<MessageExt> messages, String consumerGroup, int batchSize, @Nullable String namespace) {
    this(messages.get(0), messages, consumerGroup, batchSize, namespace);
  }

  private RocketMqConsumerRequest(
      MessageExt message,
      List<MessageExt> messages,
      String consumerGroup,
      int batchSize,
      @Nullable String namespace) {
    this.message = message;
    this.messages = messages;
    this.consumerGroup = RocketMqNamespaceUtil.withoutNamespace(consumerGroup, namespace);
    this.batchSize = batchSize;
    this.namespace = namespace == null ? "" : namespace;
  }

  MessageExt getMessage() {
    return message;
  }

  List<MessageExt> getMessages() {
    return messages;
  }

  /** Returns whether this request accounts for more than one message. */
  boolean isBatch() {
    return messages.size() > 1;
  }

  String getConsumerGroup() {
    return consumerGroup;
  }

  int getBatchSize() {
    return batchSize;
  }

  String getNamespace() {
    return namespace;
  }

  @Nullable
  String getDestination() {
    if (!destinationInitialized) {
      destination = commonValue(messages, MessageExt::getTopic);
      destinationInitialized = true;
    }
    return destination;
  }

  @Nullable
  String getMessageId() {
    if (!messageIdInitialized) {
      messageId = commonValue(messages, MessageExt::getMsgId);
      messageIdInitialized = true;
    }
    return messageId;
  }

  @Nullable
  String getMessageTag() {
    if (!messageTagInitialized) {
      messageTag = commonValue(messages, MessageExt::getTags);
      messageTagInitialized = true;
    }
    return messageTag;
  }

  @Nullable
  private static String commonValue(
      List<MessageExt> messages, Function<MessageExt, String> valueExtractor) {
    Iterator<MessageExt> iterator = messages.iterator();
    String value = valueExtractor.apply(iterator.next());
    while (iterator.hasNext()) {
      if (!Objects.equals(value, valueExtractor.apply(iterator.next()))) {
        return null;
      }
    }
    return value;
  }
}
