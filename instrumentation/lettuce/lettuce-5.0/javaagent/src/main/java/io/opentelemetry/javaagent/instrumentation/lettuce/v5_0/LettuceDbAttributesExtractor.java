/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.db.RedisCommandSanitizer;
import io.opentelemetry.instrumentation.api.instrumenter.db.DbAttributesExtractor;
import io.opentelemetry.instrumentation.lettuce.common.LettuceArgSplitter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

final class LettuceDbAttributesExtractor
    extends DbAttributesExtractor<RedisCommand<?, ?, ?>, Void> {
  @Override
  protected String system(RedisCommand<?, ?, ?> request) {
    return SemanticAttributes.DbSystemValues.REDIS;
  }

  @Override
  @Nullable
  protected String user(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  protected String name(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  @Nullable
  protected String connectionString(RedisCommand<?, ?, ?> request) {
    return null;
  }

  @Override
  protected String statement(RedisCommand<?, ?, ?> request) {
    String command = LettuceInstrumentationUtil.getCommandName(request);
    List<String> args =
        request.getArgs() == null
            ? Collections.emptyList()
            : LettuceArgSplitter.splitArgs(request.getArgs().toCommandString());
    return RedisCommandSanitizer.sanitize(command, args);
  }

  @Override
  protected String operation(RedisCommand<?, ?, ?> request) {
    return request.getType().name();
  }
}
