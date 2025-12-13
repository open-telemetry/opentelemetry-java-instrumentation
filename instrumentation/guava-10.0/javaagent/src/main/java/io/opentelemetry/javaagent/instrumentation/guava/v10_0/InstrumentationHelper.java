/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava.v10_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.guava.v10_0.GuavaAsyncOperationEndStrategy;

public final class InstrumentationHelper {
  static {
    asyncOperationEndStrategy =
        GuavaAsyncOperationEndStrategy.builder()
            .setCaptureExperimentalSpanAttributes(
                DeclarativeConfigUtil.getBoolean(
                        GlobalOpenTelemetry.get(), "java", "guava", "experimental_span_attributes")
                    .orElse(false))
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
