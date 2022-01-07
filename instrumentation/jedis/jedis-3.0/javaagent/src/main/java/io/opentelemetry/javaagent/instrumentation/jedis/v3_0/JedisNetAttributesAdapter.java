/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesAdapter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.annotation.Nullable;

final class JedisNetAttributesAdapter
    extends InetSocketAddressNetClientAttributesAdapter<JedisRequest, Void> {

  @Override
  @Nullable
  public InetSocketAddress getAddress(JedisRequest jedisRequest, @Nullable Void unused) {
    Socket socket = jedisRequest.getConnection().getSocket();
    if (socket != null && socket.getRemoteSocketAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) socket.getRemoteSocketAddress();
    }
    return null;
  }

  @Override
  public String transport(JedisRequest jedisRequest, @Nullable Void unused) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }
}
