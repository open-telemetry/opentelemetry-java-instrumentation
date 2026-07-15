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
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;

class RedissonBatchState {
  private static final Logger logger = Logger.getLogger(RedissonBatchState.class.getName());

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "redisson"));

  private final List<CapturedCommand> commands = new ArrayList<>();
  private boolean finished;

  public synchronized void add(
      int index, RedisCommand<?> command, Codec codec, Object[] parameters) {
    if (finished) {
      return;
    }
    if ("DISCARD".equals(command.getName())) {
      discard();
      return;
    }
    commands.add(
        new CapturedCommand(index, command.getName(), sanitize(command, codec, parameters)));
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
    commands.sort(Comparator.comparingInt(command -> command.index));
    List<String> commandNames = new ArrayList<>(commands.size());
    List<String> queryTexts = new ArrayList<>(commands.size());
    for (CapturedCommand command : commands) {
      commandNames.add(command.name);
      queryTexts.add(command.queryText);
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
    private final int index;
    private final String name;
    private final String queryText;

    private CapturedCommand(int index, String name, String queryText) {
      this.index = index;
      this.name = name;
      this.queryText = queryText;
    }
  }
}
