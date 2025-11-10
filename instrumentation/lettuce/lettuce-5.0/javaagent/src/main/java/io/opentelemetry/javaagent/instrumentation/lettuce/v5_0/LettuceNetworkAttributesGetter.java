/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.LettuceSingletons.COMMAND_CONNECTION_INFO;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

public final class LettuceNetworkAttributesGetter
    implements ServerAttributesGetter<RedisCommand<?, ?, ?>> {

  @Override
  @Nullable
  public String getServerAddress(RedisCommand<?, ?, ?> redisCommand) {
    RedisURI redisUri = COMMAND_CONNECTION_INFO.get(redisCommand);
    return redisUri != null ? redisUri.getHost() : null;
  }

  @Override
  @Nullable
  public Integer getServerPort(RedisCommand<?, ?, ?> redisCommand) {
    RedisURI redisUri = COMMAND_CONNECTION_INFO.get(redisCommand);
    return redisUri != null ? redisUri.getPort() : null;
  }
}
