/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.annotation.Nullable;

final class JedisNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<JedisRequest, Void> {

  @Override
  public String getTransport(JedisRequest jedisRequest, @Nullable Void unused) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getPeerName(JedisRequest jedisRequest) {
    return jedisRequest.getConnection().getHost();
  }

  @Override
  public Integer getPeerPort(JedisRequest jedisRequest) {
    return jedisRequest.getConnection().getPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(
      JedisRequest jedisRequest, @Nullable Void unused) {
    Socket socket = jedisRequest.getConnection().getSocket();
    if (socket != null && socket.getRemoteSocketAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) socket.getRemoteSocketAddress();
    }
    return null;
  }
}
