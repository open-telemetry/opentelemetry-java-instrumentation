/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava;

import com.google.common.util.concurrent.ListenableFuture;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;

public enum GuavaAsyncSpanEndStrategy implements AsyncSpanEndStrategy {
  INSTANCE;

  @Override
  public boolean supports(Class<?> returnType) {
    return ListenableFuture.class.isAssignableFrom(returnType);
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {
    ListenableFuture<?> future = (ListenableFuture<?>) returnValue;
    if (future.isDone()) {
      endSpan(tracer, context, future);
    } else {
      future.addListener(() -> endSpan(tracer, context, future), Runnable::run);
    }
    return future;
  }

  private void endSpan(BaseTracer tracer, Context context, ListenableFuture<?> future) {
    try {
      future.get();
      tracer.end(context);
    } catch (Throwable exception) {
      tracer.endExceptionally(context, exception);
    }
  }
}
