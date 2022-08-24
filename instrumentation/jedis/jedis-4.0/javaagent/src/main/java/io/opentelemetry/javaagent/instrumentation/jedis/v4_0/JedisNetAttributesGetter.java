/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class JedisNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<JedisRequest, Void> {

  @Override
  public String transport(JedisRequest jedisRequest, @Nullable Void unused) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String peerName(JedisRequest jedisRequest, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(JedisRequest jedisRequest, @Nullable Void unused) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getPeerAddress(JedisRequest jedisRequest, @Nullable Void unused) {
    SocketAddress socketAddress = jedisRequest.getRemoteSocketAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) socketAddress;
    }
    return null;
  }
}
