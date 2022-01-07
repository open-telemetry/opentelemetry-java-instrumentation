/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesAdapter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class JedisNetAttributesAdapter
    extends InetSocketAddressNetClientAttributesAdapter<JedisRequest, Void> {

  @Override
  @Nullable
  public InetSocketAddress getAddress(JedisRequest jedisRequest, @Nullable Void unused) {
    SocketAddress socketAddress = jedisRequest.getRemoteSocketAddress();
    if (socketAddress != null && socketAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) socketAddress;
    }
    return null;
  }

  @Override
  public String transport(JedisRequest jedisRequest, @Nullable Void unused) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }
}
