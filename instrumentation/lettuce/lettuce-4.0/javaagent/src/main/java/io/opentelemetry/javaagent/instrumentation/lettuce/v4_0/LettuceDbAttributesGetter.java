/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbClientAttributesGetter;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues;
import java.net.InetSocketAddress;
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
