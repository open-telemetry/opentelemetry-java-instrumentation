/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactor;

import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategies;
import io.opentelemetry.instrumentation.reactor.ReactorAsyncSpanEndStrategy;
import io.opentelemetry.instrumentation.reactor.TracingOperator;
import net.bytebuddy.asm.Advice;

public class ReactorHooksAdvice {
  @Advice.OnMethodExit(suppress = Throwable.class)
  public static void postStaticInitializer() {
    TracingOperator.registerOnEachOperator();
    AsyncSpanEndStrategies.getInstance().registerStrategy(ReactorAsyncSpanEndStrategy.INSTANCE);
  }
}
