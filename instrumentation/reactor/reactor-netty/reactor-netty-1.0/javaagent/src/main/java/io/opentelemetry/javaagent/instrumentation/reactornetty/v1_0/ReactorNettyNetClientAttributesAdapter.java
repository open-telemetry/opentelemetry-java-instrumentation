/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesAdapter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientResponse;

final class ReactorNettyNetClientAttributesAdapter
    extends InetSocketAddressNetClientAttributesAdapter<HttpClientConfig, HttpClientResponse> {

  @Nullable
  @Override
  public String transport(HttpClientConfig request, @Nullable HttpClientResponse response) {
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getAddress(
      HttpClientConfig request, @Nullable HttpClientResponse response) {

    // we're making use of the fact that HttpClientOperations is both a Connection and an
    // HttpClientResponse
    if (response instanceof Connection) {
      Connection connection = (Connection) response;
      SocketAddress address = connection.channel().remoteAddress();
      if (address instanceof InetSocketAddress) {
        return (InetSocketAddress) address;
      }
    }
    return null;
  }
}
