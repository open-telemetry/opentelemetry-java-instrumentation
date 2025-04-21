/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common.client;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.thrift.common.ThriftRequest;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public final class ThriftClientNetworkAttributesGetter
    implements ServerAttributesGetter<ThriftRequest>,
        NetworkAttributesGetter<ThriftRequest, Integer> {

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      ThriftRequest request, @Nullable Integer integer) {
    Socket socket = request.getSocket();
    if (socket == null) {
      return null;
    }
    SocketAddress address = socket.getRemoteSocketAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
