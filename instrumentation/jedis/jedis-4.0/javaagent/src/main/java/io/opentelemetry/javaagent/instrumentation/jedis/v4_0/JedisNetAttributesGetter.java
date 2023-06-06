/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class JedisNetAttributesGetter implements NetClientAttributesGetter<JedisRequest, Void> {

  @Nullable
  @Override
  public String getServerAddress(JedisRequest jedisRequest) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(JedisRequest jedisRequest) {
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getServerInetSocketAddress(
      JedisRequest jedisRequest, @Nullable Void unused) {
    SocketAddress socketAddress = jedisRequest.getRemoteSocketAddress();
    if (socketAddress instanceof InetSocketAddress) {
      return (InetSocketAddress) socketAddress;
    }
    return null;
  }
}
