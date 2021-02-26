/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import io.lettuce.core.RedisURI;

public class LettuceConnectionDatabaseClientTracer
    extends LettuceAbstractDatabaseClientTracer<String> {
  private static final LettuceConnectionDatabaseClientTracer TRACER =
      new LettuceConnectionDatabaseClientTracer();

  public static LettuceConnectionDatabaseClientTracer tracer() {
    return TRACER;
  }

  @Override
  protected String sanitizeStatement(String command) {
    return command;
  }

  @Override
  protected String spanName(RedisURI connection, String command, String ignored) {
    return command;
  }
}
