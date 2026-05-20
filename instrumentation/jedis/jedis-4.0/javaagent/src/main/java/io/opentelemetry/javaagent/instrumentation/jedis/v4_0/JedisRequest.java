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
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisRequestContext;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoValue
public abstract class JedisRequest {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "jedis"));

  @Nullable private SocketAddress remoteSocketAddress;

  public static JedisRequest create(ProtocolCommand command, List<byte[]> args) {
    return create(null, command, args);
  }

  public static JedisRequest create(
      @Nullable Object connection, ProtocolCommand command, List<byte[]> args) {
    JedisConnectionInfo connectionInfo = getConnectionInfo(connection);
    return new AutoValue_JedisRequest(
        command,
        args,
        connection,
        connectionInfo != null ? connectionInfo.getServerAddress() : null,
        connectionInfo != null ? connectionInfo.getServerPort() : null,
        JedisRequestContext.databaseIndex(
            connection, connectionInfo != null ? connectionInfo.getDatabaseIndex() : null));
  }

  public static JedisRequest create(CommandArguments commandArguments) {
    return create(null, commandArguments);
  }

  public static JedisRequest create(
      @Nullable Object connection, CommandArguments commandArguments) {
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
    return create(connection, command, arguments);
  }

  @Nullable
  private static JedisConnectionInfo getConnectionInfo(@Nullable Object connection) {
    return connection instanceof Connection
        ? JedisSingletons.connectionInfo((Connection) connection)
        : null;
  }

  public abstract ProtocolCommand getCommand();

  public abstract List<byte[]> getArgs();

  @Nullable
  abstract Object getConnection();

  @Nullable
  public abstract String getServerAddress();

  @Nullable
  public abstract Integer getServerPort();

  @Nullable
  public abstract Long getDatabaseIndex();

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
