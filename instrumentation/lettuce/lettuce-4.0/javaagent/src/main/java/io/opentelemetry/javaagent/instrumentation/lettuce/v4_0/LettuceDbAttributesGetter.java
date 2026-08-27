/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

class LettuceDbAttributesGetter implements DbClientAttributesGetter<RedisCommand<?, ?, ?>, Void> {

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

  @Deprecated // to be removed in 3.0
  @Override
  @Nullable
  public String getDbName(RedisCommand<?, ?, ?> request) {
    // old semconv reports the redis database index as db.redis.database_index, not db.name
    return null;
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
  public String getServerAddress(RedisCommand<?, ?, ?> request) {
    if (emitStableDatabaseSemconv()) {
      RedisServerTarget target = LettuceSingletons.COMMAND_TARGET.get(request);
      return target != null ? target.getAddress() : null;
    }
    InetSocketAddress serverAddress = LettuceSingletons.COMMAND_ADDRESS.get(request);
    return serverAddress != null ? serverAddress.getHostString() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RedisCommand<?, ?, ?> request) {
    if (emitStableDatabaseSemconv()) {
      RedisServerTarget target = LettuceSingletons.COMMAND_TARGET.get(request);
      return target != null ? target.getPort() : null;
    }
    InetSocketAddress serverAddress = LettuceSingletons.COMMAND_ADDRESS.get(request);
    return serverAddress != null ? serverAddress.getPort() : null;
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(RedisCommand<?, ?, ?> request, @Nullable Void unused) {
    InetSocketAddress serverAddress = LettuceSingletons.COMMAND_ADDRESS.get(request);
    return serverAddress != null && !serverAddress.isUnresolved()
        ? serverAddress.getAddress().getHostAddress()
        : null;
  }

  @Nullable
  @Override
  public Integer getNetworkPeerPort(RedisCommand<?, ?, ?> request, @Nullable Void unused) {
    InetSocketAddress serverAddress = LettuceSingletons.COMMAND_ADDRESS.get(request);
    return serverAddress != null && !serverAddress.isUnresolved() ? serverAddress.getPort() : null;
  }
}
