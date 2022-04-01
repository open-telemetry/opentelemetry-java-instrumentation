/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch;

import io.opentelemetry.instrumentation.api.config.Config;

public final class SpringBatchInstrumentationConfig {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-batch-3.0";

  // the item level instrumentation is very chatty so it's disabled by default
  private static final boolean ITEM_TRACING_ENABLED =
      Config.get()
          .getBoolean(
              "otel.instrumentation.spring-batch.item.enabled", /* defaultEnabled= */ false);
  private static final boolean CREATE_ROOT_SPAN_FOR_CHUNK =
      Config.get()
          .getBoolean(
              "otel.instrumentation.spring-batch.experimental.chunk.new-trace",
              /* defaultEnabled= */ false);

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
