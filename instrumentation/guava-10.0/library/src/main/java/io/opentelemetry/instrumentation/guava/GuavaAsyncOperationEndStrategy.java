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
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.instrumentation.api.tracer.async.AsyncSpanEndStrategy;
import java.util.function.BiConsumer;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class GuavaAsyncOperationEndStrategy
    implements AsyncOperationEndStrategy, AsyncSpanEndStrategy {
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("guava.canceled");

  public static GuavaAsyncOperationEndStrategy create() {
    return newBuilder().build();
  }

  public static GuavaAsyncOperationEndStrategyBuilder newBuilder() {
    return new GuavaAsyncOperationEndStrategyBuilder();
  }

  private final boolean captureExperimentalSpanAttributes;

  GuavaAsyncOperationEndStrategy(boolean captureExperimentalSpanAttributes) {
    this.captureExperimentalSpanAttributes = captureExperimentalSpanAttributes;
  }

  @Override
  public boolean supports(Class<?> returnType) {
    return ListenableFuture.class.isAssignableFrom(returnType);
  }

  @Override
  public <REQUEST, RESPONSE> Object end(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      Object asyncValue,
      Class<RESPONSE> responseType) {

    ListenableFuture<?> future = (ListenableFuture<?>) asyncValue;
    end(
        context,
        future,
        (result, error) ->
            instrumenter.end(context, request, tryToGetResponse(responseType, result), error));
    return future;
  }

  @Override
  public Object end(BaseTracer tracer, Context context, Object returnValue) {
    ListenableFuture<?> future = (ListenableFuture<?>) returnValue;
    end(
        context,
        future,
        (result, error) -> {
          if (error == null) {
            tracer.end(context);
          } else {
            tracer.endExceptionally(context, error);
          }
        });
    return future;
  }

  private void end(Context context, ListenableFuture<?> future, BiConsumer<Object, Throwable> end) {
    if (future.isDone()) {
      if (future.isCancelled()) {
        if (captureExperimentalSpanAttributes) {
          Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
        }
        end.accept(null, null);
      } else {
        try {
          Object response = Uninterruptibles.getUninterruptibly(future);
          end.accept(response, null);
        } catch (Throwable exception) {
          end.accept(null, exception);
        }
      }
    } else {
      future.addListener(() -> end(context, future, end), Runnable::run);
    }
  }

  @Nullable
  private static <RESPONSE> RESPONSE tryToGetResponse(Class<RESPONSE> responseType, Object result) {
    if (responseType.isInstance(result)) {
      return responseType.cast(result);
    }
    return null;
  }
}
