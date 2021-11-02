/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.args.Rawable;
import redis.clients.jedis.commands.ProtocolCommand;

@AutoValue
public abstract class JedisRequest {

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

  public String getOperation() {
    ProtocolCommand command = getCommand();
    if (command instanceof Protocol.Command) {
      return ((Protocol.Command) command).name();
    } else {
      // Protocol.Command is the only implementation in the Jedis lib as of 3.1 but this will save
      // us if that changes
      return new String(command.getRaw(), StandardCharsets.UTF_8);
    }
  }

  public String getStatement() {
    return RedisCommandSanitizer.sanitize(getOperation(), getArgs());
  }

  private SocketAddress remoteSocketAddress;

  public void setSocket(Socket socket) {
    if (socket != null) {
      remoteSocketAddress = socket.getRemoteSocketAddress();
    }
  }

  public SocketAddress getRemoteSocketAddress() {
    return remoteSocketAddress;
  }
}
