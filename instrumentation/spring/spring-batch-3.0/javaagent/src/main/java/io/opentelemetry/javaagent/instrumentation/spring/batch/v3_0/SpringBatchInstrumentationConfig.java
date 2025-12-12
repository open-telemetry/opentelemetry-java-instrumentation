/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;

public final class SpringBatchInstrumentationConfig {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-batch-3.0";

  // the item level instrumentation is very chatty so it's disabled by default
  private static final boolean ITEM_TRACING_ENABLED =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "spring_batch", "item", "enabled")
          .orElse(false);

  private static final boolean CREATE_ROOT_SPAN_FOR_CHUNK =
      DeclarativeConfigUtil.getBoolean(
              GlobalOpenTelemetry.get(), "java", "spring_batch", "chunk/development", "new_trace")
          .orElse(false);

  public static String instrumentationName() {
    return INSTRUMENTATION_NAME;
  }

  public static boolean shouldTraceItems() {
    return ITEM_TRACING_ENABLED;
  }

  public static boolean shouldCreateRootSpanForChunk() {
    return CREATE_ROOT_SPAN_FOR_CHUNK;
  }

  private SpringBatchInstrumentationConfig() {}
}
