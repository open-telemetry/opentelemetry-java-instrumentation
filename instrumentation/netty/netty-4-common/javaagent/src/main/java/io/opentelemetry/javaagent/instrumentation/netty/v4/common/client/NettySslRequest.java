/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v4.common.client;

import com.google.auto.value.AutoValue;
import io.netty.channel.Channel;
import java.net.SocketAddress;
import javax.annotation.Nullable;

@AutoValue
public abstract class NettySslRequest {

  static NettySslRequest create(Channel channel) {
    return new AutoValue_NettySslRequest(channel, channel.remoteAddress());
  }

  String spanName() {
    return "SSL handshake";
  }

  abstract Channel channel();

  @Nullable
  abstract SocketAddress remoteAddress();
}
