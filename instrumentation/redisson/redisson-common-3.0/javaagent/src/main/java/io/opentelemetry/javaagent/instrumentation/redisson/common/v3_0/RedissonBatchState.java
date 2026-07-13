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
import java.util.logging.Logger;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;

public class RedissonBatchState {
  private static final Logger logger = Logger.getLogger(RedissonBatchState.class.getName());

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "redisson"));

  private final List<String> commandNames = new ArrayList<>();
  private final List<String> queryTexts = new ArrayList<>();

  public synchronized void add(RedisCommand<?> command, Codec codec, Object[] parameters) {
    commandNames.add(command.getName());
    queryTexts.add(sanitize(command, codec, parameters));
  }

  public synchronized RedissonBatchRequest createRequest(Object options) {
    if (!isAtomic(options) || commandNames.isEmpty()) {
      return null;
    }
    return RedissonBatchRequest.create(commandNames, queryTexts);
  }

  static boolean isAtomic(Object options) {
    if (options == null) {
      return false;
    }
    try {
      Method method = options.getClass().getMethod("getExecutionMode");
      Object executionMode = method.invoke(options);
      return executionMode != null && executionMode.toString().endsWith("_ATOMIC");
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
}
