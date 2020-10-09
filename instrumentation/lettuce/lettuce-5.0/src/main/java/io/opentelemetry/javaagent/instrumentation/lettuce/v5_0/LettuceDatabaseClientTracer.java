/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.lettuce.v5_0;

import io.lettuce.core.protocol.RedisCommand;

public class LettuceDatabaseClientTracer
    extends LettuceAbstractDatabaseClientTracer<RedisCommand<?, ?, ?>> {
  public static final LettuceDatabaseClientTracer TRACER = new LettuceDatabaseClientTracer();

  @Override
  protected String normalizeQuery(RedisCommand<?, ?, ?> command) {
    return LettuceInstrumentationUtil.getCommandName(command);
  }
}
