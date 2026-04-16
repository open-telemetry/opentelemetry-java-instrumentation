/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import javax.annotation.Nullable;

public class RabbitChannelAndMethodHolder {
  @Nullable private ChannelAndMethod channelAndMethod;

  @Nullable
  public ChannelAndMethod getChannelAndMethod() {
    return channelAndMethod;
  }

  public void setChannelAndMethod(@Nullable ChannelAndMethod channelAndMethod) {
    this.channelAndMethod = channelAndMethod;
  }
}
