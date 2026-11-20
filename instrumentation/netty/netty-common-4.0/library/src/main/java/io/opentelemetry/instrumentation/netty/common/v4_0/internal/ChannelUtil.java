/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal;

import io.netty.channel.Channel;
import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.api.semconv.network.internal.SocketAddressUtil;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ChannelUtil {

  @Nullable
  public static String getNetworkTransport(@Nullable Channel channel) {
    return getNetworkTransport(channel, channel != null ? channel.remoteAddress() : null);
  }

  @Nullable
  public static String getNetworkTransport(
      @Nullable Channel channel, @Nullable SocketAddress remoteAddress) {
    if (channel == null) {
      return null;
    }
    String transport = SocketAddressUtil.getNetworkTransport(channel.localAddress(), remoteAddress);
    if (transport != null) {
      return transport;
    }
    return channel instanceof DatagramChannel ? "udp" : "tcp";
  }

  private ChannelUtil() {}
}
