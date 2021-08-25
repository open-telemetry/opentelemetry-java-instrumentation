/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.Socket;
import org.checkerframework.checker.nullness.qual.Nullable;

final class JedisNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<JedisRequest, Void> {

  @Override
  public @Nullable InetSocketAddress getAddress(JedisRequest jedisRequest, @Nullable Void unused) {
    Socket socket = jedisRequest.getConnection().getSocket();
    if (socket != null && socket.getRemoteSocketAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) socket.getRemoteSocketAddress();
    }
    return null;
  }

  @Override
  public String transport(JedisRequest jedisRequest) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }
}
