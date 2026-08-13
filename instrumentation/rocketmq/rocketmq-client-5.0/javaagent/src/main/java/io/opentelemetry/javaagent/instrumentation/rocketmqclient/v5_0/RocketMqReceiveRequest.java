/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import apache.rocketmq.v2.ReceiveMessageRequest;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.apache.rocketmq.client.java.message.MessageViewImpl;

class RocketMqReceiveRequest {

  @Nullable private final String destination;
  @Nullable private final String namespace;
  private final String consumerGroup;
  private final List<MessageView> messages;

  private RocketMqReceiveRequest(
      @Nullable String destination,
      @Nullable String namespace,
      String consumerGroup,
      List<MessageView> messages) {
    this.destination = destination;
    this.namespace = namespace;
    this.consumerGroup = consumerGroup;
    this.messages = messages;
  }

  static RocketMqReceiveRequest create(ReceiveMessageRequest request, List<MessageView> messages) {
    return new RocketMqReceiveRequest(
        request.getMessageQueue().getTopic().getName(),
        request.getMessageQueue().getTopic().getResourceNamespace(),
        request.getGroup().getName(),
        messages);
  }

  /**
   * Creates a request for an application-initiated pull, where the destination comes from the
   * received messages and falls back to {@code subscribedTopic} when the pull came back empty.
   */
  static RocketMqReceiveRequest create(
      String consumerGroup, @Nullable String subscribedTopic, List<MessageView> messages) {
    if (messages.isEmpty()) {
      return new RocketMqReceiveRequest(subscribedTopic, null, consumerGroup, messages);
    }
    MessageView message = messages.get(0);
    String namespace =
        ((MessageViewImpl) message).getMessageQueue().getTopicResource().getNamespace();
    return new RocketMqReceiveRequest(message.getTopic(), namespace, consumerGroup, messages);
  }

  @Nullable
  String getDestination() {
    return destination;
  }

  @Nullable
  String getNamespace() {
    return namespace;
  }

  String getConsumerGroup() {
    return consumerGroup;
  }

  List<MessageView> getMessages() {
    return messages;
  }
}
