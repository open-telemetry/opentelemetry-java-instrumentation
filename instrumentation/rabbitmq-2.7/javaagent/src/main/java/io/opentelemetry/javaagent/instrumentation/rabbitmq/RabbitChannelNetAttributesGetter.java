/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation") // have to use the deprecated Net*AttributesGetter for now
public class RabbitChannelNetAttributesGetter
    implements io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
        ChannelAndMethod, Void> {

  @Nullable
  @Override
  public String getSockFamily(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    if (channelAndMethod.getChannel().getConnection().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkType(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    InetAddress address = channelAndMethod.getChannel().getConnection().getAddress();
    if (address instanceof Inet4Address) {
      return "ipv6";
    } else if (address instanceof Inet6Address) {
      return "ipv6";
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return channelAndMethod.getChannel().getConnection().getAddress().getHostAddress();
  }

  @Override
  public Integer getNetworkPeerPort(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return channelAndMethod.getChannel().getConnection().getPort();
  }
}
