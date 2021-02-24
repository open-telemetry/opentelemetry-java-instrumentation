/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

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
}
