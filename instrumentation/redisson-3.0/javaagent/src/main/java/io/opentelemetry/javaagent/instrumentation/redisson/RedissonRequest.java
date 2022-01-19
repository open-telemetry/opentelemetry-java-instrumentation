/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;
import org.redisson.misc.RPromise;

@AutoValue
public abstract class RedissonRequest {

  public static RedissonRequest create(InetSocketAddress address, Object command) {
    return new AutoValue_RedissonRequest(address, command);
  }

  public abstract InetSocketAddress getAddress();

  public abstract Object getCommand();

  @Nullable
  public String getOperation() {
    Object command = getCommand();
    if (command instanceof CommandData) {
      return ((CommandData<?, ?>) command).getCommand().getName();
    } else if (command instanceof CommandsData) {
      CommandsData commandsData = (CommandsData) command;
      if (commandsData.getCommands().size() == 1) {
        return commandsData.getCommands().get(0).getCommand().getName();
      }
    }
    return null;
  }

  @Nullable
  public String getStatement() {
    List<String> sanitizedStatements = sanitizeStatement();
    switch (sanitizedStatements.size()) {
      case 0:
        return null;
        // optimize for the most common case
      case 1:
        return sanitizedStatements.get(0);
      default:
        return String.join(";", sanitizedStatements);
    }
  }

  private List<String> sanitizeStatement() {
    Object command = getCommand();
    // get command
    if (command instanceof CommandsData) {
      List<CommandData<?, ?>> commands = ((CommandsData) command).getCommands();
      return commands.stream()
          .map(RedissonRequest::normalizeSingleCommand)
          .collect(Collectors.toList());
    } else if (command instanceof CommandData) {
      return singletonList(normalizeSingleCommand((CommandData<?, ?>) command));
    }
    return emptyList();
  }

  private static String normalizeSingleCommand(CommandData<?, ?> command) {
    Object[] commandParams = command.getParams();
    List<Object> args = new ArrayList<>(commandParams.length + 1);
    if (command.getCommand().getSubName() != null) {
      args.add(command.getCommand().getSubName());
    }
    for (Object param : commandParams) {
      if (param instanceof ByteBuf) {
        try {
          // slice() does not copy the actual byte buffer, it only returns a readable/writable
          // "view" of the original buffer (i.e. read and write marks are not shared)
          ByteBuf buf = ((ByteBuf) param).slice();
          // state can be null here: no Decoders used by Codecs use it
          args.add(command.getCodec().getValueDecoder().decode(buf, null));
        } catch (Exception ignored) {
          args.add("?");
        }
      } else {
        args.add(param);
      }
    }
    return RedisCommandSanitizer.sanitize(command.getCommand().getName(), args);
  }

  @Nullable
  public RPromise<?> getPromise() {
    Object command = getCommand();
    if (command instanceof CommandData) {
      return ((CommandData<?, ?>) command).getPromise();
    } else if (command instanceof CommandsData) {
      return ((CommandsData) command).getPromise();
    }
    return null;
  }
}
