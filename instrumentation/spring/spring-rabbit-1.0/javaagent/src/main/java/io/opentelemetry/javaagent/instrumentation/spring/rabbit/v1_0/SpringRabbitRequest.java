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

  public SpringRabbitRequest(Channel channel, Message message) {
    this.channel = channel;
    this.message = message;
  }

  Channel getChannel() {
    return channel;
  }

  Message getMessage() {
    return message;
  }
}
