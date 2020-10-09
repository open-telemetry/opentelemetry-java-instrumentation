/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

public class LettuceConnectionDatabaseClientTracer
    extends LettuceAbstractDatabaseClientTracer<String> {
  public static final LettuceConnectionDatabaseClientTracer TRACER =
      new LettuceConnectionDatabaseClientTracer();

  @Override
  protected String normalizeQuery(String query) {
    return query;
  }
}
