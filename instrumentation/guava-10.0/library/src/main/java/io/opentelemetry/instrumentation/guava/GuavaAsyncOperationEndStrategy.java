/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.guava;

import static io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndSupport.tryToGetResponse;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.annotation.support.async.AsyncOperationEndStrategy;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;

public final class GuavaAsyncOperationEndStrategy implements AsyncOperationEndStrategy {
  private static final AttributeKey<Boolean> CANCELED_ATTRIBUTE_KEY =
      AttributeKey.booleanKey("guava.canceled");

  public static GuavaAsyncOperationEndStrategy create() {
    return builder().build();
  }

  public static GuavaAsyncOperationEndStrategyBuilder builder() {
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
    end(instrumenter, context, request, future, responseType);
    return future;
  }

  private <REQUEST, RESPONSE> void end(
      Instrumenter<REQUEST, RESPONSE> instrumenter,
      Context context,
      REQUEST request,
      ListenableFuture<?> future,
      Class<RESPONSE> responseType) {
    if (future.isDone()) {
      if (future.isCancelled()) {
        if (captureExperimentalSpanAttributes) {
          Span.fromContext(context).setAttribute(CANCELED_ATTRIBUTE_KEY, true);
        }
        instrumenter.end(context, request, null, null);
      } else {
        try {
          Object response = Uninterruptibles.getUninterruptibly(future);
          instrumenter.end(context, request, tryToGetResponse(responseType, response), null);
        } catch (Throwable exception) {
          instrumenter.end(context, request, null, exception);
        }
      }
    } else {
      future.addListener(
          () -> end(instrumenter, context, request, future, responseType), Runnable::run);
    }
  }
}
