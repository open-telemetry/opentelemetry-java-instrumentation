/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.util;

import io.opentelemetry.instrumentation.api.semconv.network.internal.SocketAddressUtil;
import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.DatagramChannel;

public class ChannelUtil {

  @Nullable
  public static String getNetworkTransport(@Nullable Channel channel) {
    if (channel == null) {
      return null;
    }
    String transport =
        SocketAddressUtil.getNetworkTransport(
            channel.getLocalAddress(), channel.getRemoteAddress());
    if (transport != null) {
      return transport;
    }
    return channel instanceof DatagramChannel ? "udp" : "tcp";
  }

  private ChannelUtil() {}
}
