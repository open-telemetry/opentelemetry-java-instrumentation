/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.v3_17;

import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.PromiseWrapper;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonBatchMarker;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonBatchRequest;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonFutureMarker;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonInstrumenterFactory;
import io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0.RedissonRequest;

public class RedissonSingletons {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.redisson-3.17";
  private static final VirtualField<PromiseWrapper<?>, RedissonBatchMarker> FUTURE_MARKER_FIELD =
      VirtualField.find(PromiseWrapper.class, RedissonBatchMarker.class);

  private static final Instrumenter<RedissonRequest, Void> instrumenter =
      RedissonInstrumenterFactory.createInstrumenter(INSTRUMENTATION_NAME);
  private static final Instrumenter<RedissonBatchRequest, Void> batchInstrumenter =
      RedissonInstrumenterFactory.createBatchInstrumenter(INSTRUMENTATION_NAME);
  private static final RedissonFutureMarker futureMarker =
      new RedissonFutureMarker(FUTURE_MARKER_FIELD);

  public static Instrumenter<RedissonRequest, Void> instrumenter() {
    return instrumenter;
  }

  public static Instrumenter<RedissonBatchRequest, Void> batchInstrumenter() {
    return batchInstrumenter;
  }

  public static RedissonFutureMarker futureMarker() {
    return futureMarker;
  }

  private RedissonSingletons() {}
}
