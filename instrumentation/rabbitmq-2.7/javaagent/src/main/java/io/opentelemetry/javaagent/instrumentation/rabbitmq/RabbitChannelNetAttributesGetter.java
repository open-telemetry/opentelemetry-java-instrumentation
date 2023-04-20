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
  public String getPeerName(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(ChannelAndMethod channelAndMethod) {
    return null;
  }

  @Nullable
  @Override
  public String getSockPeerAddr(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return channelAndMethod.getChannel().getConnection().getAddress().getHostAddress();
  }

  @Override
  public Integer getSockPeerPort(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return channelAndMethod.getChannel().getConnection().getPort();
  }

  @Nullable
  @Override
  public String getSockFamily(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    if (channelAndMethod.getChannel().getConnection().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }
}
