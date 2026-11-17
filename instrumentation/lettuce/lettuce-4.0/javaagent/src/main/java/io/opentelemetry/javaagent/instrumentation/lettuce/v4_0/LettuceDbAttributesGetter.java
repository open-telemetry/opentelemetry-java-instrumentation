/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class LettuceDbAttributesGetter
    implements DbClientAttributesGetter<RedisCommand<?, ?, ?>, Void> {

  @Override
  public String getDbSystemName(RedisCommand<?, ?, ?> request) {
    return DbSystemNameIncubatingValues.REDIS;
  }

  @Override
  @Nullable
  public String getDbNamespace(RedisCommand<?, ?, ?> request) {
    // Lettuce does not expose database changes made through SELECT, so report the index established
    // when the connection was created.
    Integer databaseIndex = LettuceSingletons.COMMAND_DATABASE_INDEX.get(request);
    return databaseIndex != null ? String.valueOf(databaseIndex) : null;
  }

  @Override
  @Nullable
  public String getDbQueryText(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  public String getDbOperationName(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }

  @Nullable
  @Override
  public String getDbResponseStatusCode(
      RedisCommand<?, ?, ?> request, @Nullable Void response, @Nullable Throwable error) {
    return LettuceRedisErrorCode.getErrorCode(error);
  }

  @Nullable
  @Override
  public String getErrorType(
      RedisCommand<?, ?, ?> request, @Nullable Void response, @Nullable Throwable error) {
    return LettuceRedisErrorCode.getErrorType(error);
  }

  @Nullable
  @Override
  public String getServerAddress(RedisCommand<?, ?, ?> request) {
    RedisServerTarget serverTarget = LettuceServerTargets.get(request);
    return serverTarget != null ? serverTarget.getAddress() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RedisCommand<?, ?, ?> request) {
    RedisServerTarget serverTarget = LettuceServerTargets.get(request);
    return serverTarget != null ? serverTarget.getPort() : null;
  }

  @Nullable
  @Override
  public SocketAddress getNetworkPeerSocketAddress(
      RedisCommand<?, ?, ?> request, @Nullable Void unused) {
    SocketAddress address = LettuceSingletons.commandPeerAddress(request);
    return address instanceof InetSocketAddress && ((InetSocketAddress) address).isUnresolved()
        ? null
        : address;
  }
}
