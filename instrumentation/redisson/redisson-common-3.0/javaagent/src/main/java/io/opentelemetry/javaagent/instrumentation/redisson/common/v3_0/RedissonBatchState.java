/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static java.util.logging.Level.FINE;

import io.netty.buffer.ByteBuf;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DbConfig;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.RedisCommandSanitizer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;

class RedissonBatchState {
  private static final Logger logger = Logger.getLogger(RedissonBatchState.class.getName());

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "redisson"));

  @Nullable
  private static final BatchOptionsAccessor batchOptionsAccessor = resolveBatchOptionsAccessor();

  @Nullable
  private static BatchOptionsAccessor resolveBatchOptionsAccessor() {
    try {
      Class<?> type =
          Class.forName(
              "org.redisson.api.BatchOptions", false, RedissonBatchState.class.getClassLoader());
      try {
        Method method = type.getMethod("getExecutionMode");
        return options -> {
          Object executionMode = method.invoke(options);
          return executionMode != null && executionMode.toString().endsWith("_ATOMIC");
        };
      } catch (NoSuchMethodException ignored) {
        Method method = type.getMethod("isAtomic");
        return options -> Boolean.TRUE.equals(method.invoke(options));
      }
    } catch (ClassNotFoundException ignored) {
      // Older versions pass the atomic flag directly instead of using BatchOptions.
      return null;
    } catch (NoSuchMethodException e) {
      logger.log(FINE, "Failed to read Redisson batch execution mode", e);
      return null;
    }
  }

  private final RedissonFutureMarker futureMarker;
  @Nullable private final Long databaseIndex;
  private final TreeMap<Integer, CapturedCommand> commands = new TreeMap<>();
  private int queryTextLength;
  private int queryTextCommandCount;
  private int queryTextCutoff = Integer.MAX_VALUE;
  private boolean finished;
  private boolean atomic;

  RedissonBatchState(RedissonFutureMarker futureMarker) {
    this(futureMarker, null);
  }

  RedissonBatchState(RedissonFutureMarker futureMarker, @Nullable Long databaseIndex) {
    this.futureMarker = futureMarker;
    this.databaseIndex = databaseIndex;
  }

  public void add(
      Object batchCommand,
      Object future,
      int index,
      RedisCommand<?> command,
      Codec codec,
      Object[] parameters) {
    CapturedCommand capturedCommand;
    synchronized (this) {
      if (finished) {
        if (atomic) {
          RedissonBatchContext.markCapturedCommand(batchCommand);
          futureMarker.mark(future);
        } else {
          RedissonBatchContext.unmarkCapturedCommand(batchCommand);
          futureMarker.unmark(future);
        }
        return;
      }
      if ("DISCARD".equals(command.getName())) {
        discard();
        return;
      }
      capturedCommand = new CapturedCommand(batchCommand, future, command.getName());
      CapturedCommand previousCommand = commands.put(index, capturedCommand);
      if (previousCommand != null) {
        removeQueryText(previousCommand);
      }
      if (index >= queryTextCutoff) {
        return;
      }
    }

    String queryText = null;
    try {
      queryText = sanitize(command, codec, parameters);
    } finally {
      commitQueryText(index, capturedCommand, queryText);
    }
  }

  private synchronized void commitQueryText(
      int index, CapturedCommand capturedCommand, @Nullable String queryText) {
    if (queryText != null && commands.get(index) == capturedCommand && index < queryTextCutoff) {
      capturedCommand.queryText = queryText;
      queryTextLength += queryText.length();
      if (queryTextCommandCount > 0) {
        queryTextLength += 2;
      }
      queryTextCommandCount++;
      while (queryTextLength > RedissonBatchRequest.QUERY_TEXT_LIMIT) {
        Map.Entry<Integer, CapturedCommand> removedEntry = commands.lowerEntry(queryTextCutoff);
        CapturedCommand removed = removedEntry.getValue();
        removeQueryText(removed);
        queryTextCutoff = removedEntry.getKey();
      }
    }
  }

  private void removeQueryText(CapturedCommand command) {
    if (command.queryText != null) {
      queryTextLength -= command.queryText.length();
      queryTextCommandCount--;
      if (queryTextCommandCount > 0) {
        queryTextLength -= 2;
      }
      command.queryText = null;
    }
  }

  public synchronized RedissonBatchRequest finish(Object options) {
    if (finished) {
      return null;
    }
    finished = true;
    atomic = isAtomic(options);
    if (!atomic || commands.isEmpty()) {
      unmarkCommands();
      clear();
      return null;
    }
    List<String> commandNames = new ArrayList<>(commands.size());
    List<String> queryTexts = new ArrayList<>(commands.size());
    boolean captureQueryText = true;
    for (CapturedCommand command : commands.values()) {
      RedissonBatchContext.markCapturedCommand(command.batchCommand);
      futureMarker.mark(command.future);
      commandNames.add(command.name);
      @Nullable String queryText = command.queryText;
      if (captureQueryText && queryText == null) {
        captureQueryText = false;
      }
      if (captureQueryText && queryText != null) {
        queryTexts.add(queryText);
      }
    }
    RedissonBatchRequest request =
        RedissonBatchRequest.create(commandNames, queryTexts, databaseIndex);
    clear();
    return request;
  }

  synchronized void discard() {
    finished = true;
    atomic = false;
    unmarkCommands();
    clear();
  }

  private void unmarkCommands() {
    for (CapturedCommand command : commands.values()) {
      RedissonBatchContext.unmarkCapturedCommand(command.batchCommand);
      futureMarker.unmark(command.future);
    }
  }

  private void clear() {
    commands.clear();
  }

  static boolean isAtomic(Object options) {
    if (options == null) {
      return false;
    }
    if (options instanceof Boolean) {
      return (Boolean) options;
    }
    if (batchOptionsAccessor == null) {
      return false;
    }
    try {
      return batchOptionsAccessor.isAtomic(options);
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Failed to read Redisson batch execution mode", e);
      return false;
    }
  }

  private static String sanitize(RedisCommand<?> command, Codec codec, Object[] parameters) {
    List<Object> args = new ArrayList<>(parameters.length + 1);
    if (command.getSubName() != null) {
      args.add(command.getSubName());
    }
    for (Object parameter : parameters) {
      if (parameter instanceof ByteBuf) {
        try {
          ByteBuf buffer = ((ByteBuf) parameter).slice();
          args.add(codec.getValueDecoder().decode(buffer, null));
        } catch (Exception ignored) {
          args.add("?");
        }
      } else {
        args.add(parameter);
      }
    }
    return sanitizer.sanitize(command.getName(), args);
  }

  private interface BatchOptionsAccessor {
    boolean isAtomic(Object options) throws ReflectiveOperationException;
  }

  private static class CapturedCommand {
    private final Object batchCommand;
    private final Object future;
    private final String name;
    @Nullable private String queryText;

    private CapturedCommand(Object batchCommand, Object future, String name) {
      this.batchCommand = batchCommand;
      this.future = future;
      this.name = name;
    }
  }
}
