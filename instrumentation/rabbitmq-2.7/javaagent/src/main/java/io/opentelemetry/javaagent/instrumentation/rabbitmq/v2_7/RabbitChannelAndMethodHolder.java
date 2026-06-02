/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import javax.annotation.Nullable;

class RabbitChannelAndMethodHolder {
  @Nullable private ChannelAndMethod channelAndMethod;

  @Nullable
  ChannelAndMethod getChannelAndMethod() {
    return channelAndMethod;
  }

  void setChannelAndMethod(@Nullable ChannelAndMethod channelAndMethod) {
    this.channelAndMethod = channelAndMethod;
  }
}
