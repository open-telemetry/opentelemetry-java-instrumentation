/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandNormalizer;
import io.opentelemetry.javaagent.instrumentation.lettuce.LettuceArgSplitter;
import java.util.Collections;
import java.util.List;

public class LettuceDatabaseClientTracer
    extends LettuceAbstractDatabaseClientTracer<RedisCommand<?, ?, ?>> {
  public static final LettuceDatabaseClientTracer TRACER = new LettuceDatabaseClientTracer();

  @Override
  protected String spanName(
      RedisURI connection, RedisCommand<?, ?, ?> query, String normalizedQuery) {
    return LettuceInstrumentationUtil.getCommandName(query);
  }

  @Override
  protected String normalizeQuery(RedisCommand<?, ?, ?> redisCommand) {
    String command = LettuceInstrumentationUtil.getCommandName(redisCommand);
    List<String> args =
        redisCommand.getArgs() == null
            ? Collections.emptyList()
            : LettuceArgSplitter.splitArgs(redisCommand.getArgs().toCommandString());
    return RedisCommandNormalizer.normalize(command, args);
  }
}
