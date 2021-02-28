/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import com.lambdaworks.redis.protocol.RedisCommand;

public class LettuceDatabaseClientTracer
    extends LettuceAbstractDatabaseClientTracer<RedisCommand<?, ?, ?>> {

  private static final LettuceDatabaseClientTracer TRACER = new LettuceDatabaseClientTracer();

  public static LettuceDatabaseClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String sanitizeStatement(RedisCommand<?, ?, ?> command) {
    return command.getType().name();
  }
}
