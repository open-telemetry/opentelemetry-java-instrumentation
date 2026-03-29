/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.guava.v10_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategies;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.guava.v10_0.GuavaAsyncOperationEndStrategy;
import io.opentelemetry.javaagent.bootstrap.executors.PropagatedContext;

public final class InstrumentationHelper {
  private static final GuavaAsyncOperationEndStrategy asyncOperationEndStrategy;

  public static final VirtualField<Runnable, PropagatedContext> PROPAGATED_CONTEXT =
      VirtualField.find(Runnable.class, PropagatedContext.class);

  static {
    asyncOperationEndStrategy =
        GuavaAsyncOperationEndStrategy.builder()
            .setCaptureExperimentalSpanAttributes(
                DeclarativeConfigUtil.getInstrumentationConfig(GlobalOpenTelemetry.get(), "guava")
                    .getBoolean("experimental_span_attributes/development", false))
            .build();

    registerAsyncSpanEndStrategy();
  }

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
