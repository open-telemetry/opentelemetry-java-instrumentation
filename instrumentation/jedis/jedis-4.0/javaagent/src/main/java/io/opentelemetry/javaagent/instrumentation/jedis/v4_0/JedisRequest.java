/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoValue
public abstract class JedisRequest {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "jedis"));

  public static JedisRequest create(ProtocolCommand command, List<byte[]> args) {
    return new AutoValue_JedisRequest(command, args);
  }

  public static JedisRequest create(CommandArguments commandArguments) {
    ProtocolCommand command = commandArguments.getCommand();
    List<byte[]> arguments = new ArrayList<>();
    boolean first = true;
    for (Rawable rawable : commandArguments) {
      if (first) {
        first = false;
        continue;
      }
      arguments.add(rawable.getRaw());
    }
    return create(command, arguments);
  }

  public abstract ProtocolCommand getCommand();

  public abstract List<byte[]> getArgs();

  public String getOperationName() {
    ProtocolCommand command = getCommand();
    if (command instanceof Protocol.Command) {
      return ((Protocol.Command) command).name();
    } else {
      // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
      // us if that changes
      return new String(command.getRaw(), UTF_8);
    }
  }

  public String getQueryText() {
    return sanitizer.sanitize(getOperationName(), getArgs());
  }

  @Nullable private SocketAddress remoteSocketAddress;

  public void setSocket(@Nullable Socket socket) {
    if (socket != null) {
      remoteSocketAddress = socket.getRemoteSocketAddress();
    }
  }

  @Nullable
  public SocketAddress getRemoteSocketAddress() {
    return remoteSocketAddress;
  }
}
