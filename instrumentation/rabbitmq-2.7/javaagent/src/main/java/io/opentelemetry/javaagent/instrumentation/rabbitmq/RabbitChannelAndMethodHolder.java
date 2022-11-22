/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

public class RabbitChannelAndMethodHolder {
  private ChannelAndMethod channelAndMethod;

  public ChannelAndMethod getChannelAndMethod() {
    return channelAndMethod;
  }

  public void setChannelAndMethod(ChannelAndMethod channelAndMethod) {
    this.channelAndMethod = channelAndMethod;
  }
}
