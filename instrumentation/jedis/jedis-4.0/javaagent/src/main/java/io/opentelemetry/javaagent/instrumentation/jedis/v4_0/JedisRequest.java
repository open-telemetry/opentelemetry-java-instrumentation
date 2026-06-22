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
import java.util.StringJoiner;
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
    String operationName = operationName(command);
    return new AutoValue_JedisRequest(
        connectionInfo != null ? connectionInfo.getServerAddress() : null,
        connectionInfo != null ? connectionInfo.getServerPort() : null,
        connectionInfo != null ? connectionInfo.getDatabaseIndex() : null,
        operationName,
        sanitizer.sanitize(operationName, args),
        null);
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

  public static JedisRequest createPipeline(List<JedisRequest> requests) {
    JedisRequest first = requests.get(0);
    JedisRequest request =
        new AutoValue_JedisRequest(
            first.getServerAddress(),
            first.getServerPort(),
            first.getDatabaseIndex(),
            pipelineOperationName(requests),
            pipelineQueryText(requests),
            requests.size() != 1 ? (long) requests.size() : null);
    request.remoteSocketAddress = first.getRemoteSocketAddress();
    return request;
  }

  @Nullable
  private static JedisConnectionInfo getConnectionInfo(@Nullable Object connection) {
    return connection instanceof Connection
        ? JedisSingletons.connectionInfo((Connection) connection)
        : null;
  }

  @Nullable
  public abstract String getServerAddress();

  @Nullable
  public abstract Integer getServerPort();

  @Nullable
  public abstract Long getDatabaseIndex();

  public abstract String getOperationName();

  public abstract String getQueryText();

  @Nullable
  public abstract Long getBatchSize();

  private static String operationName(ProtocolCommand command) {
    if (command instanceof Protocol.Command) {
      return ((Protocol.Command) command).name();
    } else {
      // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
      // us if that changes
      return new String(command.getRaw(), UTF_8);
    }
  }

  private static String pipelineOperationName(List<JedisRequest> requests) {
    if (requests.size() == 1) {
      return requests.get(0).getOperationName();
    }
    String commonOperationName = requests.get(0).getOperationName();
    for (int i = 1; i < requests.size(); i++) {
      if (!commonOperationName.equals(requests.get(i).getOperationName())) {
        return "PIPELINE";
      }
    }
    return "PIPELINE " + commonOperationName;
  }

  private static String pipelineQueryText(List<JedisRequest> requests) {
    StringJoiner joiner = new StringJoiner(";");
    for (JedisRequest request : requests) {
      joiner.add(request.getQueryText());
    }
    return joiner.toString();
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
