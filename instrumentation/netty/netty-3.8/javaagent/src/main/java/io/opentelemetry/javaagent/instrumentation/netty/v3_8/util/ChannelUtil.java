/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.v3_8.util;

import javax.annotation.Nullable;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.socket.DatagramChannel;

public final class ChannelUtil {

  public static String getNetworkTransport(@Nullable Channel channel) {
    if (channel == null) {
      return null;
    }
    return channel instanceof DatagramChannel ? "udp" : "tcp";
  }

  private ChannelUtil() {}
}
