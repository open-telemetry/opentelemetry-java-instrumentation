/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thrift.common.server;

import io.opentelemetry.instrumentation.api.instrumenter.network.ClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
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
    implements ServerAttributesGetter<ThriftRequest, Integer>,
        ClientAttributesGetter<ThriftRequest, Integer> {

  @Override
  @Nullable
  public InetSocketAddress getClientInetSocketAddress(
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
