/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
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
    RedisURI redisUri = LettuceSingletons.COMMAND_URI.get(request);
    return redisUri != null ? String.valueOf(redisUri.getDatabase()) : null;
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
    InetSocketAddress serverAddress = LettuceSingletons.COMMAND_ADDRESS.get(request);
    return serverAddress != null ? serverAddress.getHostString() : null;
  }

  @Nullable
  @Override
  public Integer getServerPort(RedisCommand<?, ?, ?> request) {
    InetSocketAddress serverAddress = LettuceSingletons.COMMAND_ADDRESS.get(request);
    return serverAddress != null ? serverAddress.getPort() : null;
  }
}
