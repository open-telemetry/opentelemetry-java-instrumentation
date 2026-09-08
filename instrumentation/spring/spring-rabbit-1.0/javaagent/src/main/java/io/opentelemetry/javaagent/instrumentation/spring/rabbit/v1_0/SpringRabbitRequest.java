/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;

public class SpringRabbitRequest {

  private final Channel channel;
  private final Message message;
  private final int batchMessageCount;

  public SpringRabbitRequest(Channel channel, Message message) {
    this(channel, message, 0);
  }

  public SpringRabbitRequest(Channel channel, Message message, int batchMessageCount) {
    this.channel = channel;
    this.message = message;
    this.batchMessageCount = batchMessageCount;
  }

  Channel getChannel() {
    return channel;
  }

  Message getMessage() {
    return message;
  }

  boolean isBatch() {
    return batchMessageCount > 0;
  }

  int getBatchMessageCount() {
    return batchMessageCount;
  }
}
