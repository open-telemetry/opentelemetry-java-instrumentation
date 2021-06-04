/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;

public final class GuavaAsyncSpanEndStrategy implements AsyncSpanEndStrategy {
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("guava.canceled");

  public static GuavaAsyncSpanEndStrategy create() {
    return newBuilder().build();
  }

  public static GuavaAsyncSpanEndStrategyBuilder newBuilder() {
    return new GuavaAsyncSpanEndStrategyBuilder();
  }

  private final boolean captureExperimentalSpanAttributes;

  GuavaAsyncSpanEndStrategy(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

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
    if (future.isCancelled()) {
      if (captureExperimentalSpanAttributes) {
        Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
      }
      tracer.end(context);
    } else {
      try {
        Uninterruptibles.getUninterruptibly(future);
        tracer.end(context);
      } catch (Throwable exception) {
        tracer.endExceptionally(context, exception);
      }
    }
  }
}
