/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.RedisChannelHandler;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.StatefulConnection;
import com.lambdaworks.redis.cluster.RedisClusterClient;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import javax.annotation.Nullable;

public class LettuceServerTargets {

  private static final VirtualField<RedisClusterClient, RedisServerTarget> CLIENT_TARGET =
      VirtualField.find(RedisClusterClient.class, RedisServerTarget.class);

  private static final VirtualField<RedisChannelHandler<?, ?>, RedisServerTarget>
      CONNECTION_TARGET = VirtualField.find(RedisChannelHandler.class, RedisServerTarget.class);

  private static final VirtualField<RedisCommand<?, ?, ?>, RedisServerTarget> COMMAND_TARGET =
      VirtualField.find(RedisCommand.class, RedisServerTarget.class);

  public static void capture(
      RedisClusterClient client, @Nullable Iterable<RedisURI> configuredUris) {
    CLIENT_TARGET.set(client, LettuceServerTarget.ofUris(configuredUris));
  }

  public static void capture(
      RedisChannelHandler<?, ?> connection, @Nullable RedisURI configuredUri) {
    CONNECTION_TARGET.set(connection, LettuceServerTarget.of(configuredUri));
  }

  public static void capture(
      RedisChannelHandler<?, ?> connection, @Nullable RedisServerTarget target) {
    CONNECTION_TARGET.set(connection, target);
  }

  static void capture(RedisCommand<?, ?, ?> command, @Nullable RedisServerTarget target) {
    COMMAND_TARGET.set(command, target);
  }

  public static void copy(RedisClusterClient client, RedisChannelHandler<?, ?> connection) {
    CONNECTION_TARGET.set(connection, CLIENT_TARGET.get(client));
  }

  public static void copy(StatefulConnection<?, ?> connection, RedisCommand<?, ?, ?> command) {
    COMMAND_TARGET.set(command, get(connection));
  }

  @Nullable
  public static RedisServerTarget get(RedisCommand<?, ?, ?> command) {
    return COMMAND_TARGET.get(command);
  }

  @Nullable
  public static RedisServerTarget get(StatefulConnection<?, ?> connection) {
    return connection instanceof RedisChannelHandler
        ? CONNECTION_TARGET.get((RedisChannelHandler<?, ?>) connection)
        : null;
  }

  private LettuceServerTargets() {}
}
