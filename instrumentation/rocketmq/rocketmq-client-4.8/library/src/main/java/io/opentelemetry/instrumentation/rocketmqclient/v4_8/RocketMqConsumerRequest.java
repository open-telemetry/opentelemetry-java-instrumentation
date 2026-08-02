/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.rocketmqclient.v4_8;

import static java.util.Collections.singletonList;

import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.common.message.MessageExt;

final class RocketMqConsumerRequest {

  private final MessageExt message;
  private final List<MessageExt> messages;
  private final String consumerGroup;
  private final int batchSize;
  private final String namespace;

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
}
