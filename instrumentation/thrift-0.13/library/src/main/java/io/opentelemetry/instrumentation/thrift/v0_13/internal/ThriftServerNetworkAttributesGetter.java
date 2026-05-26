/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.v0_13.internal;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftRequest;
import io.opentelemetry.instrumentation.thrift.v0_13.ThriftResponse;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class ThriftServerNetworkAttributesGetter
    implements ServerAttributesGetter<ThriftRequest>,
        NetworkAttributesGetter<ThriftRequest, ThriftResponse> {

  @Nullable
  @Override
  public String getServerAddress(ThriftRequest request) {
    if (request.getLocalAddress() instanceof InetSocketAddress) {
      return ((InetSocketAddress) request.getLocalAddress()).getHostString();
    }
    return null;
  }

  @Override
  @Nullable
  public Integer getServerPort(ThriftRequest request) {
    if (request.getLocalAddress() instanceof InetSocketAddress) {
      return ((InetSocketAddress) request.getLocalAddress()).getPort();
    }
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      ThriftRequest request, @Nullable ThriftResponse response) {
    SocketAddress address = request.getLocalAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      ThriftRequest request, @Nullable ThriftResponse response) {
    SocketAddress address = request.getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
