/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17_2;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.redisson.RedissonInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.redisson.RedissonRequest;

public final class RedissonSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-3.17.2";

  private static final Instrumenter<RedissonRequest, Void> INSTRUMENTER =
      RedissonInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  public static Instrumenter<RedissonRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private RedissonSingletons() {}
}
