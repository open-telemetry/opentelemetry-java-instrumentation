/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class JedisNetworkAttributesGetter implements NetworkAttributesGetter<JedisRequest, Void> {

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      JedisRequest jedisRequest, @Nullable Void unused) {
    SocketAddress socketAddress = jedisRequest.getRemoteSocketAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) socketAddress;
    }
    return null;
  }
}
