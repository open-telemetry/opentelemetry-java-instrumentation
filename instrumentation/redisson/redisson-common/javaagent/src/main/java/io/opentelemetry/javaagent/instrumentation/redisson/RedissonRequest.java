/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

@AutoValue
public abstract class RedissonRequest {

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(AgentCommonConfig.get().isStatementSanitizationEnabled());

  public static RedissonRequest create(InetSocketAddress address, Object command) {
    return new AutoValue_RedissonRequest(address, command);
  }

  @Nullable
  public abstract InetSocketAddress getAddress();

  public abstract Object getCommand();

  @Nullable
  public String getOperation() {
    Object command = getCommand();
    if (command instanceof CommandData) {
      return ((CommandData<?, ?>) command).getCommand().getName();
    } else if (command instanceof CommandsData) {
      CommandsData commandsData = (CommandsData) command;
      List<CommandData<?, ?>> commands = commandsData.getCommands();
      if (commands.isEmpty()) {
        return null;
      }
      if (commands.size() == 1) {
        return commands.get(0).getCommand().getName();
      }
      // For batch operations with multiple commands, return batch operation names only when
      // stable database semconv is enabled
      if (SemconvStability.emitStableDatabaseSemconv()) {
        // Check if all operations are the same
        String firstOperation = commands.get(0).getCommand().getName();
        boolean allSameOperation =
            commands.stream().allMatch(cmd -> cmd.getCommand().getName().equals(firstOperation));
        if (allSameOperation) {
          return "BATCH " + firstOperation;
        }
        // Different operations - return null so span name falls back to db system name
        // per
        // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/db/database-spans.md
        return null;
      }
      // Legacy behavior: return null for batch operations
      return null;
    }
    return null;
  }

  @Nullable
  public Long getBatchSize() {
    // Batch size attribute is only part of stable database semconv
    if (!SemconvStability.emitStableDatabaseSemconv()) {
      return null;
    }
    Object command = getCommand();
    if (command instanceof CommandsData) {
      CommandsData commandsData = (CommandsData) command;
      int size = commandsData.getCommands().size();
      // Only return batch size if it's actually a batch (2 or more operations)
      // AND all operations are the same (when operations differ, there's no batch operation name)
      if (size > 1) {
        String firstOperation = commandsData.getCommands().get(0).getCommand().getName();
        boolean allSameOperation =
            commandsData.getCommands().stream()
                .allMatch(cmd -> cmd.getCommand().getName().equals(firstOperation));
        return allSameOperation ? (long) size : null;
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
    return sanitizer.sanitize(command.getCommand().getName(), args);
  }

  @Nullable
  public PromiseWrapper<?> getPromiseWrapper() {
    CompletionStage<?> promise = getPromise();
    if (promise instanceof PromiseWrapper) {
      return (PromiseWrapper<?>) promise;
    }
    return null;
  }

  @Nullable
  private CompletionStage<?> getPromise() {
    Object command = getCommand();
    if (command instanceof CommandData && COMMAND_DATA_GET_PROMISE != null) {
      try {
        return (CompletionStage<?>) COMMAND_DATA_GET_PROMISE.invoke(command);
      } catch (Throwable ignored) {
        return null;
      }
    } else if (command instanceof CommandsData && COMMANDS_DATA_GET_PROMISE != null) {
      try {
        return (CompletionStage<?>) COMMANDS_DATA_GET_PROMISE.invoke(command);
      } catch (Throwable ignored) {
        return null;
      }
    }
    return null;
  }

  private static final MethodHandle COMMAND_DATA_GET_PROMISE =
      findGetPromiseMethod(CommandData.class);
  private static final MethodHandle COMMANDS_DATA_GET_PROMISE =
      findGetPromiseMethod(CommandsData.class);

  private static MethodHandle findGetPromiseMethod(Class<?> commandClass) {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    try {
      Class<?> promiseClass =
          Class.forName(
              "org.redisson.misc.RPromise", false, RedissonRequest.class.getClassLoader());
      // try versions older than 3.16.8
      return lookup.findVirtual(commandClass, "getPromise", MethodType.methodType(promiseClass));
    } catch (NoSuchMethodException | ClassNotFoundException e) {
      // in 3.16.8 CommandsData#getPromise() and CommandData#getPromise() return type was changed in
      // a backwards-incompatible way from RPromise to CompletableFuture
      try {
        return lookup.findVirtual(
            commandClass, "getPromise", MethodType.methodType(CompletableFuture.class));
      } catch (NoSuchMethodException | IllegalAccessException ignored) {
        return null;
      }
    } catch (IllegalAccessException ignored) {
      return null;
    }
  }
}
