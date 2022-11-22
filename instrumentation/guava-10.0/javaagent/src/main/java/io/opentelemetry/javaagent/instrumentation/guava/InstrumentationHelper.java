/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava;

import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.guava.GuavaAsyncOperationEndStrategy;
import io.opentelemetry.javaagent.bootstrap.internal.InstrumentationConfig;

public final class InstrumentationHelper {
  static {
    asyncOperationEndStrategy =
        GuavaAsyncOperationEndStrategy.builder()
            .setCaptureExperimentalSpanAttributes(
                InstrumentationConfig.get()
                    .getBoolean("otel.instrumentation.guava.experimental-span-attributes", false))
            .build();

    registerAsyncSpanEndStrategy();
  }

  private static final GuavaAsyncOperationEndStrategy asyncOperationEndStrategy;

  private static void registerAsyncSpanEndStrategy() {
    AsyncOperationEndStrategies.instance().registerStrategy(asyncOperationEndStrategy);
  }

  /**
   * This method is invoked to trigger the runtime system to execute the static initializer block
   * ensuring that the {@link GuavaAsyncOperationEndStrategy} is registered exactly once.
   */
  public static void initialize() {}

  private InstrumentationHelper() {}
}
