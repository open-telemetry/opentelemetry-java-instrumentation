/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.annotation.Nullable;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest, Void> {

  @Override
  public String getDbSystemName(JedisRequest request) {
    return DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  public String getDbNamespace(JedisRequest request) {
    return null;
  }

  @Override
  public String getDbQueryText(JedisRequest request) {
    return request.getQueryText();
  }

  @Override
  public String getDbOperationName(JedisRequest request) {
    return request.getOperationName();
  }

  @Override
  public String getServerAddress(JedisRequest request) {
    return request.getConnection().getHost();
  }

  @Override
  public Integer getServerPort(JedisRequest request) {
    return request.getConnection().getPort();
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      JedisRequest request, @Nullable Void unused) {
    Socket socket = request.getConnection().getSocket();
    if (socket != null && socket.getRemoteSocketAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) socket.getRemoteSocketAddress();
    }
    return null;
  }
}
