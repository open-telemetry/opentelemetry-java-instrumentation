/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import com.google.auto.value.AutoValue;
import io.netty.channel.Channel;
import io.opentelemetry.javaagent.instrumentation.netty.common.Timer;
import java.net.SocketAddress;
import javax.annotation.Nullable;

@AutoValue
public abstract class NettySslRequest {

  static NettySslRequest create(Channel channel) {
    return new AutoValue_NettySslRequest(Timer.start(), channel, channel.remoteAddress());
  }

  String spanName() {
    return "SSL handshake";
  }

  abstract Timer timer();

  abstract Channel channel();

  @Nullable
  abstract SocketAddress remoteAddress();
}
