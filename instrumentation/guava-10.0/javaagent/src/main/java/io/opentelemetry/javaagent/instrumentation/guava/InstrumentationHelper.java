/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategies;
import io.opentelemetry.instrumentation.guava.GuavaAsyncSpanEndStrategy;

public final class InstrumentationHelper {
  static {
    registerAsyncSpanEndStrategy();
  }

  private static void registerAsyncSpanEndStrategy() {
    AsyncSpanEndStrategies.getInstance()
        .registerStrategy(
            GuavaAsyncSpanEndStrategy.newBuilder()
                .setCaptureExperimentalSpanAttributes(
                    Config.get()
                        .getBooleanProperty(
                            "otel.instrumentation.guava.experimental-span-attributes", false))
                .build());
  }

  /**
   * This method is invoked to trigger the runtime system to execute the static initializer block
   * ensuring that the {@link GuavaAsyncSpanEndStrategy} is registered exactly once.
   */
  public static void initialize() {}

  private InstrumentationHelper() {}
}
