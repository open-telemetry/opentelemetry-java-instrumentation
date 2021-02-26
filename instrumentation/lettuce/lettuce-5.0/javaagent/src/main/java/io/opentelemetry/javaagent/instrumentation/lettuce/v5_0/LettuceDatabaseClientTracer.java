/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.javaagent.instrumentation.api.db.RedisCommandSanitizer;
import io.opentelemetry.javaagent.instrumentation.lettuce.LettuceArgSplitter;
import java.util.Collections;
import java.util.List;

public class LettuceDatabaseClientTracer
    extends LettuceAbstractDatabaseClientTracer<RedisCommand<?, ?, ?>> {
  private static final LettuceDatabaseClientTracer TRACER = new LettuceDatabaseClientTracer();

  public static LettuceDatabaseClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String sanitizeStatement(RedisCommand<?, ?, ?> redisCommand) {
    String command = LettuceInstrumentationUtil.getCommandName(redisCommand);
    List<String> args =
        redisCommand.getArgs() == null
            ? Collections.emptyList()
            : LettuceArgSplitter.splitArgs(redisCommand.getArgs().toCommandString());
    return RedisCommandSanitizer.sanitize(command, args);
  }

  @Override
  protected String spanName(
      RedisURI connection, RedisCommand<?, ?, ?> command, String sanitizedStatement) {
    return LettuceInstrumentationUtil.getCommandName(command);
  }
}
