/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.common.v4_0.internal.client;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.netty.common.v4_0.internal.ChannelUtil;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class NettySslNetAttributesGetter implements NetworkAttributesGetter<NettySslRequest, Void> {

  @Override
  public String getNetworkTransport(NettySslRequest request, @Nullable Void unused) {
    return ChannelUtil.getNetworkTransport(request.channel(), request.remoteAddress());
  }

  @Nullable
  @Override
  public SocketAddress getNetworkPeerSocketAddress(NettySslRequest request, @Nullable Void unused) {
    return request.remoteAddress();
  }
}
