/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rocketmqclient.v5_0;

import org.apache.rocketmq.client.apis.message.MessageView;

class RocketMqAckRequest {

  private final String consumerGroup;
  private final MessageView message;

  RocketMqAckRequest(String consumerGroup, MessageView message) {
    this.consumerGroup = consumerGroup;
    this.message = message;
  }

  String getConsumerGroup() {
    return consumerGroup;
  }

  MessageView getMessage() {
    return message;
  }
}
