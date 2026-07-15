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

  private final TreeMap<Integer, CapturedCommand> commands = new TreeMap<>();
  private int queryTextLength;
  private int queryTextCommandCount;
  private int queryTextCutoff = Integer.MAX_VALUE;
  private boolean finished;

  public synchronized void add(
      Object batchCommand, int index, RedisCommand<?> command, Codec codec, Object[] parameters) {
    if (finished) {
      return;
    }
    if ("DISCARD".equals(command.getName())) {
      discard();
      return;
    }
    CapturedCommand capturedCommand = new CapturedCommand(batchCommand, command.getName());
    commands.put(index, capturedCommand);
    if (index >= queryTextCutoff) {
      return;
    }

    capturedCommand.queryText = sanitize(command, codec, parameters);
    queryTextLength += capturedCommand.queryText.length();
    if (queryTextCommandCount > 0) {
      queryTextLength += 2;
    }
    queryTextCommandCount++;
    while (queryTextLength > RedissonBatchRequest.QUERY_TEXT_LIMIT) {
      Map.Entry<Integer, CapturedCommand> removedEntry = commands.lowerEntry(queryTextCutoff);
      CapturedCommand removed = removedEntry.getValue();
      if (removed.queryText != null) {
        queryTextLength -= removed.queryText.length();
        queryTextCommandCount--;
      }
      if (queryTextCommandCount > 0) {
        queryTextLength -= 2;
      }
      removed.queryText = null;
      queryTextCutoff = removedEntry.getKey();
    }
  }

  public synchronized RedissonBatchRequest finish(Object options) {
    if (finished) {
      return null;
    }
    finished = true;
    if (!isAtomic(options) || commands.isEmpty()) {
      clear();
      return null;
    }
    List<String> commandNames = new ArrayList<>(commands.size());
    List<String> queryTexts = new ArrayList<>(commands.size());
    for (CapturedCommand command : commands.values()) {
      RedissonBatchContext.markCapturedCommand(command.batchCommand);
      commandNames.add(command.name);
      if (command.queryText != null) {
        queryTexts.add(command.queryText);
      }
    }
    RedissonBatchRequest request = RedissonBatchRequest.create(commandNames, queryTexts);
    clear();
    return request;
  }

  synchronized void discard() {
    finished = true;
    clear();
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
    try {
      try {
        Method method = options.getClass().getMethod("getExecutionMode");
        Object executionMode = method.invoke(options);
        return executionMode != null && executionMode.toString().endsWith("_ATOMIC");
      } catch (NoSuchMethodException ignored) {
        Method method = options.getClass().getMethod("isAtomic");
        return Boolean.TRUE.equals(method.invoke(options));
      }
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

  private static class CapturedCommand {
    private final Object batchCommand;
    private final String name;
    @Nullable private String queryText;

    private CapturedCommand(Object batchCommand, String name) {
      this.batchCommand = batchCommand;
      this.name = name;
    }
  }
}
