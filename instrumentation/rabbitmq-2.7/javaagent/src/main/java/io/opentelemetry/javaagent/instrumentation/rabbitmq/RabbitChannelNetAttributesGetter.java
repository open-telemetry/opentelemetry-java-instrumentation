/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.Inet6Address;
import javax.annotation.Nullable;

public class RabbitChannelNetAttributesGetter
    implements NetClientAttributesGetter<ChannelAndMethod, Void> {

  @Nullable
  @Override
  public String transport(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerAddr(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return channelAndMethod.getChannel().getConnection().getAddress().getHostAddress();
  }

  @Nullable
  @Override
  public String sockPeerName(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return null;
  }

  @Override
  public Integer sockPeerPort(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return channelAndMethod.getChannel().getConnection().getPort();
  }

  @Nullable
  @Override
  public String sockFamily(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    if (channelAndMethod.getChannel().getConnection().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }
}
