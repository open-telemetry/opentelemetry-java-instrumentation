/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.annotation.Nullable;

final class JedisDbAttributesGetter implements DbClientAttributesGetter<JedisRequest, Void> {

  @Override
  @Nullable
  public String getErrorType(
      JedisRequest request, @Nullable Void response, @Nullable Throwable error) {
    return null;
  }

  @Override
  public String getDbSystemName(JedisRequest request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(JedisRequest request) {
    Long databaseIndex = request.getDatabaseIndex();
    return databaseIndex != null ? String.valueOf(databaseIndex) : null;
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
  @Nullable
  public Long getDbOperationBatchSize(JedisRequest request) {
    return request.getBatchSize();
  }

  @Override
  @Nullable
  public String getServerAddress(JedisRequest request) {
    RedisServerTarget target = request.getServerTarget();
    return target != null ? target.getAddress() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(JedisRequest request) {
    RedisServerTarget target = request.getServerTarget();
    return target != null ? target.getPort() : null;
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
