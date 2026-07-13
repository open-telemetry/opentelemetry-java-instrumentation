/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.logging.Level.FINE;

import com.google.auto.value.AutoValue;
import io.netty.buffer.ByteBuf;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.redisson.client.protocol.CommandData;
import org.redisson.client.protocol.CommandsData;

@AutoValue
public abstract class RedissonRequest {

  private static final Logger logger = Logger.getLogger(RedissonRequest.class.getName());

  private static final String MULTI = "MULTI";
  private static final String PIPELINE = "PIPELINE";

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "redisson"));
  // after combined query text length has reached this size we won't append further commands
  // note that RedisCommandSanitizer already limits the size of sanitized commands
  private static final int LIMIT = 32 * 1024;

  @Nullable
  private static final MethodHandle COMMAND_DATA_GET_PROMISE =
      findGetPromiseMethod(CommandData.class);

  @Nullable
  private static final MethodHandle COMMANDS_DATA_GET_PROMISE =
      findGetPromiseMethod(CommandsData.class);

  @Nullable
  private static MethodHandle findGetPromiseMethod(Class<?> commandClass) {
    MethodHandles.Lookup lookup = MethodHandles.publicLookup();
    try {
      Class<?> promiseClass =
          Class.forName(
              "org.redisson.misc.RPromise", false, RedissonRequest.class.getClassLoader());
      // try versions older than 3.16.8
      return lookup.findVirtual(commandClass, "getPromise", MethodType.methodType(promiseClass));
    } catch (NoSuchMethodException | ClassNotFoundException ignored) {
      // in 3.16.8 CommandsData#getPromise() and CommandData#getPromise() return type was changed in
      // a backwards-incompatible way from RPromise to CompletableFuture
      try {
        return lookup.findVirtual(
            commandClass, "getPromise", MethodType.methodType(CompletableFuture.class));
      } catch (NoSuchMethodException | IllegalAccessException ignore) {
        return null;
      }
    } catch (IllegalAccessException ignored) {
      return null;
    }
  }

  public static RedissonRequest create(@Nullable InetSocketAddress address, Object command) {
    return new AutoValue_RedissonRequest(address, command);
  }

  @Nullable
  public abstract InetSocketAddress getAddress();

  public abstract Object getCommand();

  @Nullable
  public String getOperationName() {
    Object command = getCommand();
    if (command instanceof CommandData) {
      return ((CommandData<?, ?>) command).getCommand().getName();
    } else if (command instanceof CommandsData) {
      CommandsData commandsData = (CommandsData) command;
      List<CommandData<?, ?>> commands = commandsData.getCommands();
      if (commands.size() == 1) {
        return commands.get(0).getCommand().getName();
      }
      return emitStableDatabaseSemconv() ? getBatchOperationName(commands) : null;
    }
    return null;
  }

  @Nullable
  public Long getOperationBatchSize() {
    Object command = getCommand();
    if (!(command instanceof CommandsData)) {
      return null;
    }
    List<CommandData<?, ?>> commands = ((CommandsData) command).getCommands();
    if (commands.isEmpty()) {
      return null;
    }
    int batchSize = commands.size();
    if (commands.get(0).getCommand().getName().equals(MULTI)) {
      // MULTI is a transaction wrapper command, not a user operation in the batch.
      batchSize--;
    }
    return batchSize > 1 ? (long) batchSize : null;
  }

  @Nullable
  private static String getBatchOperationName(List<CommandData<?, ?>> commands) {
    if (commands.size() < 2) {
      return null;
    }

    String firstCommandName = commands.get(0).getCommand().getName();
    String batchOperationName = firstCommandName.equals(MULTI) ? MULTI : PIPELINE;
    int firstBatchCommandIndex = firstCommandName.equals(MULTI) ? 1 : 0;
    String commonCommandName = getCommonCommandName(commands, firstBatchCommandIndex);
    return commonCommandName == null
        ? batchOperationName
        : batchOperationName + " " + commonCommandName;
  }

  @Nullable
  public String getQueryText() {
    List<String> sanitizedQueries = sanitizeQuery();
    switch (sanitizedQueries.size()) {
      case 0:
        return null;
      // optimize for the most common case
      case 1:
        return sanitizedQueries.get(0);
      default:
        return String.join(batchQuerySeparator(), sanitizedQueries);
    }
  }

  private static String batchQuerySeparator() {
    return emitStableDatabaseSemconv() ? "; " : ";";
  }

  private List<String> sanitizeQuery() {
    Object command = getCommand();
    // get command
    if (command instanceof CommandsData) {
      int length = 0;
      List<CommandData<?, ?>> commands = ((CommandsData) command).getCommands();
      List<String> normalizedCommands = new ArrayList<>(commands.size());
      for (CommandData<?, ?> singleCommand : commands) {
        String s = normalizeSingleCommand(singleCommand);
        length += s.length();
        normalizedCommands.add(s);
        if (length > LIMIT) {
          break;
        }
      }
      return normalizedCommands;
    } else if (command instanceof CommandData) {
      return singletonList(normalizeSingleCommand((CommandData<?, ?>) command));
    }
    return emptyList();
  }

  @Nullable
  private static String getCommonCommandName(
      List<CommandData<?, ?>> commands, int firstBatchCommandIndex) {
    if (firstBatchCommandIndex >= commands.size()) {
      return null;
    }

    String commonCommandName = commands.get(firstBatchCommandIndex).getCommand().getName();
    for (int i = firstBatchCommandIndex + 1; i < commands.size(); i++) {
      String commandName = commands.get(i).getCommand().getName();
      if (!commandName.equals(commonCommandName)) {
        return null;
      }
    }
    return commonCommandName;
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

  public boolean isMarkedBatchCommand() {
    if (RedissonBatchContext.isMarkedCommand(getCommand())) {
      return true;
    }
    CompletionStage<?> promise = getPromise();
    if (RedissonBatchContext.isMarkedFuture(promise)) {
      return true;
    }
    Object command = getCommand();
    if (command instanceof CommandsData) {
      for (CommandData<?, ?> singleCommand : ((CommandsData) command).getCommands()) {
        if (RedissonBatchContext.isMarkedFuture(getPromise(singleCommand))) {
          return true;
        }
      }
    }
    return false;
  }

  @Nullable
  private CompletionStage<?> getPromise() {
    Object command = getCommand();
    if (command instanceof CommandData && COMMAND_DATA_GET_PROMISE != null) {
      try {
        return (CompletionStage<?>) COMMAND_DATA_GET_PROMISE.invoke(command);
      } catch (Throwable t) {
        logger.log(FINE, "Failed to get Redisson command promise", t);
        return null;
      }
    } else if (command instanceof CommandsData && COMMANDS_DATA_GET_PROMISE != null) {
      try {
        return (CompletionStage<?>) COMMANDS_DATA_GET_PROMISE.invoke(command);
      } catch (Throwable t) {
        logger.log(FINE, "Failed to get Redisson commands promise", t);
        return null;
      }
    }
    return null;
  }

  @Nullable
  private static CompletionStage<?> getPromise(CommandData<?, ?> command) {
    if (COMMAND_DATA_GET_PROMISE == null) {
      return null;
    }
    try {
      return (CompletionStage<?>) COMMAND_DATA_GET_PROMISE.invoke(command);
    } catch (Throwable t) {
      logger.log(FINE, "Failed to get Redisson command promise", t);
      return null;
    }
  }
}
