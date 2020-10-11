/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v4_0;

import com.lambdaworks.redis.protocol.RedisCommand;

public class LettuceDatabaseClientTracer
    extends LettuceAbstractDatabaseClientTracer<RedisCommand<?, ?, ?>> {

  public static final LettuceDatabaseClientTracer TRACER = new LettuceDatabaseClientTracer();

  @Override
  protected String normalizeQuery(RedisCommand<?, ?, ?> command) {
    return command.getType().name();
  }
}
