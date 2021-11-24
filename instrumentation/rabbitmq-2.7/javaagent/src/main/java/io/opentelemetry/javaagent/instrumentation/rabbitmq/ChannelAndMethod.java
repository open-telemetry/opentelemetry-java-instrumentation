/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.google.auto.value.AutoValue;
import com.rabbitmq.client.Channel;

@AutoValue
public abstract class ChannelAndMethod {

  public static ChannelAndMethod create(Channel channel, String method) {
    return new AutoValue_ChannelAndMethod(channel, method);
  }

  abstract Channel getChannel();

  abstract String getMethod();
}
