/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_0;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonRequest;

public class RedissonSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-3.0";

  private static final Instrumenter<RedissonRequest, Void> instrumenter =
      RedissonInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);

  public static Instrumenter<RedissonRequest, Void> instrumenter() {
    return instrumenter;
  }

  private RedissonSingletons() {}
}
