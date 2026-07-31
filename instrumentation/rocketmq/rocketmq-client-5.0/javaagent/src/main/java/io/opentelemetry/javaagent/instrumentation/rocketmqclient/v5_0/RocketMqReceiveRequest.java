/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import apache.rocketmq.v2.ReceiveMessageRequest;
import java.util.List;
import org.apache.rocketmq.client.apis.message.MessageView;

public final class RocketMqReceiveRequest {

  private final ReceiveMessageRequest request;
  private final List<MessageView> messages;

  private RocketMqReceiveRequest(ReceiveMessageRequest request, List<MessageView> messages) {
    this.request = request;
    this.messages = messages;
  }

  public static RocketMqReceiveRequest create(
      ReceiveMessageRequest request, List<MessageView> messages) {
    return new RocketMqReceiveRequest(request, messages);
  }

  ReceiveMessageRequest getRequest() {
    return request;
  }

  List<MessageView> getMessages() {
    return messages;
  }
}
