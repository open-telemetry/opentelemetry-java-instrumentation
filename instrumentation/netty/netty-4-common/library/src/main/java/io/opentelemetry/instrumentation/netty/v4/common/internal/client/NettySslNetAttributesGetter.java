/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.netty.v4.common.internal.ChannelUtil;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation") // have to use the deprecated Net*AttributesGetter for now
final class NettySslNetAttributesGetter
    implements io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
        NettySslRequest, Void> {

  @Override
  public String getTransport(NettySslRequest request, @Nullable Void unused) {
    return request.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Override
  public String getNetworkTransport(NettySslRequest request, @Nullable Void unused) {
    return ChannelUtil.getNetworkTransport(request.channel());
  }

  @Nullable
  @Override
  public String getServerAddress(NettySslRequest nettySslRequest) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(NettySslRequest nettySslRequest) {
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      NettySslRequest request, @Nullable Void unused) {
    if (request.remoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) request.remoteAddress();
    }
    return null;
  }
}
