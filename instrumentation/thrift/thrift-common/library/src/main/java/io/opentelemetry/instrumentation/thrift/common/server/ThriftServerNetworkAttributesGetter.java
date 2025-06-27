/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common.server;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.thrift.common.ThriftRequest;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThriftServerNetworkAttributesGetter
    implements ServerAttributesGetter<ThriftRequest>,
        NetworkAttributesGetter<ThriftRequest, Integer> {

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      ThriftRequest request, @Nullable Integer status) {
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
