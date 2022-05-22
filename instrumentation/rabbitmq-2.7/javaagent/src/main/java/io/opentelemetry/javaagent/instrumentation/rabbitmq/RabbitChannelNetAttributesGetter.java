/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
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
    // not using InetAddress.getHostName() since that can trigger reverse name lookup
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return channelAndMethod.getChannel().getConnection().getPort();
  }

  @Nullable
  @Override
  public String peerIp(ChannelAndMethod channelAndMethod, @Nullable Void unused) {
    return channelAndMethod.getChannel().getConnection().getAddress().getHostAddress();
  }
}
