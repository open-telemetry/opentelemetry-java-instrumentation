/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import io.opentelemetry.instrumentation.api.config.Config;
import java.util.List;

public final class SpringBatchInstrumentationConfig {
  private static final List<String> INSTRUMENTATION_NAMES =
      unmodifiableList(asList("spring-batch", "spring-batch-3.0"));

  // the item level instrumentation is very chatty so it's disabled by default
  private static final boolean ITEM_TRACING_ENABLED =
      Config.get().isInstrumentationPropertyEnabled(instrumentationNames(), "item.enabled", false);
  private static final boolean CREATE_ROOT_SPAN_FOR_CHUNK =
      Config.get()
          .isInstrumentationPropertyEnabled(
              instrumentationNames(), "experimental.chunk.new-trace", false);

  public static List<String> instrumentationNames() {
    return INSTRUMENTATION_NAMES;
  }

  public static boolean shouldTraceItems() {
    return ITEM_TRACING_ENABLED;
  }

  public static boolean shouldCreateRootSpanForChunk() {
    return CREATE_ROOT_SPAN_FOR_CHUNK;
  }

  private SpringBatchInstrumentationConfig() {}
}
