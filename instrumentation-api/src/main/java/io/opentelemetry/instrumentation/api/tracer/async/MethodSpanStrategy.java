/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.tracer.async;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.ImplicitContextKeyed;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public interface MethodSpanStrategy extends ImplicitContextKeyed {
  ContextKey<MethodSpanStrategy> CONTEXT_KEY =
      ContextKey.named("opentelemetry-spring-autoconfigure-aspects-method-span-strategy");

  Object end(BaseTracer tracer, Context context, Object result);

  @Override
  default Context storeInContext(Context context) {
    return context.with(CONTEXT_KEY, this);
  }

  static MethodSpanStrategy fromContext(Context context) {
    MethodSpanStrategy methodSpanStrategy = context.get(CONTEXT_KEY);
    return methodSpanStrategy != null ? methodSpanStrategy : synchronous();
  }

  static MethodSpanStrategy synchronous() {
    return SynchronousMethodSpanStrategy.INSTANCE;
  }

  static MethodSpanStrategy forCompletionStage() {
    return CompletionStageMethodSpanStrategy.INSTANCE;
  }

  static MethodSpanStrategy forCompletableFuture() {
    return CompletableFutureMethodSpanStrategy.INSTANCE;
  }
}
