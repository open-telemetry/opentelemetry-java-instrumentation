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

class RedissonBatchState {
  private static final Logger logger = Logger.getLogger(RedissonBatchState.class.getName());

  private static final RedisCommandSanitizer sanitizer =
      RedisCommandSanitizer.create(
          DbConfig.isQuerySanitizationEnabled(GlobalOpenTelemetry.get(), "redisson"));

  private final List<String> commandNames = new ArrayList<>();
  private final List<String> queryTexts = new ArrayList<>();
  private int queryTextLength;
  private boolean captureQueryText = true;
  private boolean finished;

  public synchronized void add(RedisCommand<?> command, Codec codec, Object[] parameters) {
    if (finished) {
      return;
    }
    if ("DISCARD".equals(command.getName())) {
      finished = true;
      return;
    }
    commandNames.add(command.getName());
    if (!captureQueryText) {
      // Query text is already complete; avoid sanitizing commands that will not be emitted.
      return;
    }

    String queryText = sanitize(command, codec, parameters);
    int separatorLength = queryTexts.isEmpty() ? 0 : 2;
    if (queryTextLength + separatorLength + queryText.length()
        > RedissonBatchRequest.QUERY_TEXT_LIMIT) {
      captureQueryText = false;
      return;
    }
    queryTexts.add(queryText);
    queryTextLength += separatorLength + queryText.length();
  }

  public synchronized RedissonBatchRequest finish(Object options) {
    if (finished) {
      return null;
    }
    finished = true;
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
